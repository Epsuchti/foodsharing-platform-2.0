package network.foodsharing.mailbox.service;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed inbound MIME message (A35): headers, recipients, first text/plain and text/html body, attachments and
 * inline parts, DSN details ({@code multipart/report; report-type=delivery-status}).
 */
public final class ParsedMail {

    /** An attachment or inline part. */
    public record Attachment(String fileName, String mimeType, byte[] bytes, String contentId) {
    }

    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>@\\s]+@[^<>\\s]+>");
    private static final Pattern FINAL_RECIPIENT = Pattern.compile("(?im)^(?:Final|Original)-Recipient:\\s*rfc822;\\s*(\\S+)");

    MimeMessage message;
    String text;
    String html;
    final List<Attachment> attachments = new ArrayList<>();
    boolean deliveryReport;
    final StringBuilder reportText = new StringBuilder();

    private ParsedMail() {
    }

    /** @throws IllegalArgumentException when the bytes are no parseable MIME message */
    public static ParsedMail parse(byte[] raw, boolean withAttachments) {
        ParsedMail parsed = new ParsedMail();
        try {
            parsed.message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            parsed.deliveryReport = isDeliveryReport(parsed.message.getContentType());
            parsed.walk(parsed.message, withAttachments);
            return parsed;
        } catch (MessagingException | IOException ex) {
            throw new IllegalArgumentException("Unparseable MIME message: " + ex.getMessage(), ex);
        }
    }

    private void walk(Part part, boolean withAttachments) throws MessagingException, IOException {
        String type = part.getContentType() == null ? "text/plain" : part.getContentType().toLowerCase(Locale.ROOT);
        String disposition = part.getDisposition();
        String fileName = decodedFileName(part);
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || fileName != null && !type.startsWith("text/plain") && !type.startsWith("text/html");
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                walk(multipart.getBodyPart(i), withAttachments);
            }
            return;
        }
        if (part.isMimeType("message/delivery-status") || part.isMimeType("text/rfc822-headers")) {
            reportText.append(readText(part)).append('\n');
            return;
        }
        if (part.isMimeType("message/rfc822") && deliveryReport) {
            Object content = part.getContent();
            if (content instanceof MimeMessage original) {
                String[] id = original.getHeader("Message-ID");
                if (id != null) {
                    reportText.append("Message-ID: ").append(id[0]).append('\n');
                }
            }
            return;
        }
        if (!attachment && part.isMimeType("text/plain") && text == null) {
            text = readText(part);
            return;
        }
        if (!attachment && part.isMimeType("text/html") && html == null) {
            html = readText(part);
            return;
        }
        String contentId = part instanceof BodyPart body && body.getHeader("Content-ID") != null
                ? MailHtmlSanitizer.stripAngles(body.getHeader("Content-ID")[0]) : null;
        if (withAttachments && (attachment || contentId != null)) {
            try (InputStream in = part.getInputStream()) {
                String mime = new ContentType(part.getContentType()).getBaseType().toLowerCase(Locale.ROOT);
                attachments.add(new Attachment(fileName == null ? "attachment" : fileName, mime, in.readAllBytes(),
                        contentId));
            }
        }
    }

    private static String readText(Part part) throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String s) {
            return s;
        }
        try (InputStream in = part.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String decodedFileName(Part part) throws MessagingException {
        String name = part.getFileName();
        if (name == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(name);
        } catch (java.io.UnsupportedEncodingException ex) {
            return name;
        }
    }

    static boolean isDeliveryReport(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("multipart/report") && lower.contains("delivery-status");
    }

    // ------------------------------------------------------------------------------------------ headers

    public String header(String name) {
        try {
            String[] values = message.getHeader(name);
            return values == null || values.length == 0 ? null : values[0].strip();
        } catch (MessagingException ex) {
            return null;
        }
    }

    public String subject() {
        try {
            String subject = message.getSubject();
            return subject == null ? "" : subject.strip();
        } catch (MessagingException ex) {
            return "";
        }
    }

    public MailAddresses.Address from() {
        try {
            Address[] from = message.getFrom();
            if (from != null && from.length > 0 && from[0] instanceof InternetAddress a) {
                return new MailAddresses.Address(a.getAddress(), a.getPersonal());
            }
        } catch (MessagingException ignored) {
            // fall through
        }
        return new MailAddresses.Address("unknown@invalid", null);
    }

    public List<MailAddresses.Address> addresses(Message.RecipientType type) {
        List<MailAddresses.Address> result = new ArrayList<>();
        try {
            Address[] addresses = message.getRecipients(type);
            if (addresses != null) {
                for (Address address : addresses) {
                    if (address instanceof InternetAddress a) {
                        result.add(new MailAddresses.Address(a.getAddress(), a.getPersonal()));
                    }
                }
            }
        } catch (MessagingException ignored) {
            // malformed header: no addresses
        }
        return result;
    }

    /** Every envelope-ish recipient: To, Cc, Bcc, Delivered-To, X-Original-To (lower case, de-duplicated). */
    public Set<String> allRecipients() {
        Set<String> result = new LinkedHashSet<>();
        for (Message.RecipientType type : List.of(Message.RecipientType.TO, Message.RecipientType.CC,
                Message.RecipientType.BCC)) {
            addresses(type).forEach(a -> result.add(a.address().toLowerCase(Locale.ROOT)));
        }
        for (String header : List.of("Delivered-To", "X-Original-To", "Envelope-To")) {
            try {
                String[] values = message.getHeader(header);
                if (values != null) {
                    for (String value : values) {
                        for (InternetAddress a : InternetAddress.parseHeader(value, false)) {
                            result.add(a.getAddress().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (MessagingException ignored) {
                // AddressException or unreadable header
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------ bounces

    public boolean isDeliveryReport() {
        return deliveryReport;
    }

    /** Message ids a bounce refers to: In-Reply-To, References, X-Original-Message-ID and ids in the report. */
    public Set<String> referencedMessageIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String header : List.of("In-Reply-To", "References", "X-Original-Message-ID")) {
            String value = header(header);
            if (value != null) {
                collectIds(value, ids);
            }
        }
        collectIds(reportText.toString(), ids);
        if (text != null) {
            collectIds(text, ids);
        }
        String own = header("Message-ID");
        if (own != null) {
            ids.remove(own);
        }
        return ids;
    }

    /** Failed recipients named in the delivery-status part (or the text of a non-standard bounce). */
    public Set<String> failedRecipients() {
        Set<String> result = new LinkedHashSet<>();
        Matcher m = FINAL_RECIPIENT.matcher(reportText + "\n" + (text == null ? "" : text));
        while (m.find()) {
            String address = m.group(1).replaceAll("[<>]", "").strip().toLowerCase(Locale.ROOT);
            try {
                new InternetAddress(address, true);
                result.add(address);
            } catch (AddressException ignored) {
                // not an address
            }
        }
        return result;
    }

    private static void collectIds(String value, Set<String> ids) {
        Matcher m = MESSAGE_ID.matcher(value);
        while (m.find()) {
            ids.add(m.group());
        }
    }
}
