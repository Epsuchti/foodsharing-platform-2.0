package network.foodsharing.notification.mail;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A rendered mail ready for a {@link MailTransport}; {@code htmlBody} may be null (text only); {@code headers} are
 * extra MIME headers (e.g. {@code List-Unsubscribe} for newsletters, {@code Message-ID}/{@code In-Reply-To} for
 * mailbox mails). {@code from} is a full address ({@code "Name <a@b>"} allowed); {@code replyTo} may be null.
 */
public record OutgoingMail(String to, String from, String subject, String textBody, String htmlBody,
                           Map<String, String> headers, String replyTo, List<Attachment> attachments) {

    /** An attachment whose bytes are read only when the transport builds the MIME message. */
    public record Attachment(String fileName, String mimeType, Supplier<byte[]> bytes) {
    }

    public OutgoingMail {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public OutgoingMail(String to, String from, String subject, String textBody, String htmlBody,
                        Map<String, String> headers) {
        this(to, from, subject, textBody, htmlBody, headers, null, List.of());
    }

    public OutgoingMail(String to, String from, String subject, String textBody, String htmlBody) {
        this(to, from, subject, textBody, htmlBody, Map.of());
    }
}
