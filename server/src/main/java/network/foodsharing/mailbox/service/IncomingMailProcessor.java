package network.foodsharing.mailbox.service;

import jakarta.mail.Message;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.mailbox.domain.MailAttachment;
import network.foodsharing.mailbox.domain.MailFolder;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxAction;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.mapper.MailboxMapper;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one inbound MIME message (A35, MB5): resolves every {@code <email_name>@domain} recipient to its mailbox
 * (unknown ones are dropped and logged), stores a copy per mailbox in INBOX (idempotent by raw hash), attachments as
 * system uploads, detects bounces and flags the referenced SENT mail and the bounced user address, then sends the
 * grouped {@code MAILBOX_NEW_MAIL} bell and a realtime {@code MAILBOX_MAIL} event to the readers.
 */
@Service
public class IncomingMailProcessor implements network.foodsharing.common.integration.InboundMailHandler {

    private static final Logger log = LoggerFactory.getLogger(IncomingMailProcessor.class);

    /** Larger mails are stored without attachments (A35). */
    static final long MAX_RAW_BYTES = 25L * 1024 * 1024;

    /** Outcome of processing one message. */
    public record Result(int stored, int duplicates, List<String> unknownRecipients, boolean bounce) {
    }

    private final MailboxRepository mailboxes;
    private final MailMessageRepository mails;
    private final MailboxAccessService access;
    private final MailAttachmentStore attachmentStore;
    private final MailHtmlSanitizer sanitizer;
    private final MailService mailService;
    private final UploadService uploads;
    private final UserService users;
    private final NotificationService notifications;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public IncomingMailProcessor(MailboxRepository mailboxes, MailMessageRepository mails, MailboxAccessService access,
                                 MailAttachmentStore attachmentStore, MailHtmlSanitizer sanitizer,
                                 MailService mailService, UploadService uploads, UserService users,
                                 NotificationService notifications, HistoryService history, AppProperties properties,
                                 Clock clock) {
        this.mailboxes = mailboxes;
        this.mails = mails;
        this.access = access;
        this.attachmentStore = attachmentStore;
        this.sanitizer = sanitizer;
        this.mailService = mailService;
        this.uploads = uploads;
        this.users = users;
        this.notifications = notifications;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    @NoHistory(reason = "delegates to process(), which records MAIL_RECEIVED / UNKNOWN_RECIPIENT / BOUNCE_RECORDED")
    public void accept(byte[] raw) {
        process(raw);
    }

    /** @throws IllegalArgumentException when {@code raw} is no MIME message */
    @Transactional
    public Result process(byte[] raw) {
        Instant now = clock.instant();
        boolean oversize = raw.length > MAX_RAW_BYTES;
        ParsedMail parsed = ParsedMail.parse(raw, !oversize);
        String hash = sha256(raw);
        String domain = properties.getMailbox().getDomain() == null ? ""
                : properties.getMailbox().getDomain().toLowerCase(Locale.ROOT);

        Map<UUID, Mailbox> targets = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (String recipient : parsed.allRecipients()) {
            int at = recipient.lastIndexOf('@');
            if (at < 1 || domain.isBlank() || !recipient.substring(at + 1).equals(domain)) {
                continue;
            }
            mailboxes.findByEmailNameIgnoreCase(recipient.substring(0, at))
                    .ifPresentOrElse(m -> targets.putIfAbsent(m.getId(), m), () -> unknown.add(recipient));
        }
        for (String recipient : unknown) {
            history.record(history.event(MailboxAction.UNKNOWN_RECIPIENT, HistoryService.SYSTEM_SUBJECT_ID)
                    .actorSystem().metadata(Map.of("recipient", recipient, "subject", abbreviate(parsed.subject(), 200),
                            "from", parsed.from().address())).build());
        }
        if (targets.isEmpty()) {
            log.info("Inbound mail without a known mailbox recipient dropped (unknown: {})", unknown);
            return new Result(0, 0, unknown, false);
        }

        List<Mailbox> fresh = targets.values().stream()
                .filter(m -> !mails.existsByMailboxIdAndRawHash(m.getId(), hash)).toList();
        int duplicates = targets.size() - fresh.size();
        if (fresh.isEmpty()) {
            return new Result(0, duplicates, unknown, false);
        }

        String sender = parsed.from().address().toLowerCase(Locale.ROOT);
        String autoSubmitted = parsed.header("Auto-Submitted");
        boolean hardBounce = parsed.isDeliveryReport() || sender.startsWith("mailer-daemon@")
                || sender.startsWith("postmaster@");
        boolean bounce = hardBounce || autoSubmitted != null && autoSubmitted.toLowerCase(Locale.ROOT)
                .startsWith("auto-replied");
        String bouncedMessageId = hardBounce ? recordBounce(parsed, now) : null;

        List<Upload> files = new ArrayList<>();
        List<ParsedMail.Attachment> parts = new ArrayList<>();
        StringBuilder notes = new StringBuilder();
        long maxUpload = properties.getUploads().getMaxSizeMb() * 1024L * 1024L;
        for (ParsedMail.Attachment part : parsed.attachments) {
            if (part.bytes().length > maxUpload) {
                notes.append("\n[Anhang ").append(part.fileName()).append(" zu gross, nicht gespeichert]");
                continue;
            }
            files.add(uploads.store(null, new UploadService.NewFile(part.fileName(), part.mimeType(), part.bytes())));
            parts.add(part);
        }
        if (oversize) {
            notes.append("\n[Mail grösser als 25 MB: Anhänge wurden nicht gespeichert]");
        }

        int stored = 0;
        for (Mailbox mailbox : fresh) {
            MailMessage mail = new MailMessage();
            mail.setMailboxId(mailbox.getId());
            mail.setFolder(MailFolder.INBOX);
            mail.setMessageId(abbreviate(parsed.header("Message-ID"), 255));
            mail.setInReplyTo(abbreviate(parsed.header("In-Reply-To"), 255));
            mail.setFromAddress(abbreviate(parsed.from().address(), 320));
            mail.setFromName(abbreviate(parsed.from().name(), 200));
            mail.setToJson(MailAddresses.toJson(parsed.addresses(Message.RecipientType.TO)));
            List<MailAddresses.Address> cc = parsed.addresses(Message.RecipientType.CC);
            mail.setCcJson(cc.isEmpty() ? null : abbreviate(MailAddresses.toJson(cc), 8000));
            String replyTo = parsed.header("Reply-To");
            mail.setReplyTo(replyTo == null ? null : abbreviate(firstAddress(replyTo), 320));
            mail.setSubject(abbreviate(parsed.subject(), 998));
            String text = parsed.text != null ? parsed.text
                    : parsed.html != null ? sanitizer.toText(sanitizer.sanitize(parsed.html)) : "";
            mail.setBodyText(abbreviate(text + notes, MailMessage.MAX_BODY));
            mail.setReceivedAt(now);
            mail.setBounce(bounce);
            mail.setBouncedMessageId(bouncedMessageId);
            mail.setRawSize((long) raw.length);
            mail.setRawHash(hash);
            mail.setAttachmentCount(files.size());
            mail = mails.save(mail);
            Map<String, String> cidToUrl = new HashMap<>();
            for (int i = 0; i < files.size(); i++) {
                MailAttachment attachment = attachmentStore.attach(mail.getId(), files.get(i), parts.get(i).contentId());
                if (attachment.getContentId() != null) {
                    cidToUrl.put(attachment.getContentId(),
                            MailboxMapper.attachmentUrl(mail.getId().toString(), attachment.getId().toString()));
                }
            }
            if (parsed.html != null) {
                mail.setBodyHtml(abbreviate(sanitizer.sanitize(parsed.html, cidToUrl::get), MailMessage.MAX_BODY));
            }
            stored++;
            history.record(event(MailboxAction.MAIL_RECEIVED, mailbox).actorSystem()
                    .metadata(Map.of("mailId", mail.getId().toString(), "from", mail.getFromAddress(),
                            "bounce", bounce, "attachments", files.size())).build());
            notifyReaders(mailbox, mail, now);
            mailService.publish(mailbox, mail, "NEW");
        }
        return new Result(stored, duplicates, unknown, bounce);
    }

    /** Flags the SENT mails a bounce refers to and the failed user addresses; returns the first referenced id. */
    private String recordBounce(ParsedMail parsed, Instant now) {
        Set<String> ids = parsed.referencedMessageIds();
        String first = null;
        if (!ids.isEmpty()) {
            for (MailMessage sent : mails.findByFolderAndMessageIdIn(MailFolder.SENT, ids)) {
                if (first == null) {
                    first = sent.getMessageId();
                }
                if (!sent.isBounce()) {
                    sent.setBounce(true);
                    Mailbox mailbox = mailboxes.findById(sent.getMailboxId()).orElse(null);
                    if (mailbox != null) {
                        history.record(event(MailboxAction.BOUNCE_RECORDED, mailbox).actorSystem()
                                .metadata(Map.of("mailId", sent.getId().toString(),
                                        "messageId", sent.getMessageId())).build());
                    }
                }
            }
        }
        for (String failed : parsed.failedRecipients()) {
            users.markEmailBounced(failed, now).ifPresent(userId -> history.record(history
                    .event(MailboxAction.BOUNCE_RECORDED, HistoryService.SYSTEM_SUBJECT_ID).actorSystem()
                    .secondarySubject(userId).metadata(Map.of("recipient", failed)).build()));
        }
        return first == null && !ids.isEmpty() ? abbreviate(ids.iterator().next(), 255) : first;
    }

    /** Grouped bell per mailbox: at most one {@code MAILBOX_NEW_MAIL} per mailbox and hour (A35). */
    private void notifyReaders(Mailbox mailbox, MailMessage mail, Instant now) {
        String identifier = "MAILBOX_NEW_MAIL-" + mailbox.getId();
        if (notifications.sentSince(identifier, now.minus(Duration.ofHours(1)))) {
            return;
        }
        Set<UUID> readers = access.readers(mailbox);
        if (readers.isEmpty()) {
            return;
        }
        notifications.notify(NotificationRequest.to(readers, NotificationType.MAILBOX_NEW_MAIL)
                .param("mailbox", mailbox.getName()).param("from", mail.getFromName() == null
                        ? mail.getFromAddress() : mail.getFromName())
                .param("subject", abbreviate(mail.getSubject(), 120))
                .link("/mailboxes/" + mailbox.getId()).identifier(identifier).icon("mail").build());
    }

    private HistoryEvent.Builder event(MailboxAction action, Mailbox mailbox) {
        HistoryEvent.Builder builder = history.event(action, mailbox.getId());
        if (mailbox.getOwnerType() == MailboxOwnerType.REGION) {
            builder.region(mailbox.getOwnerId());
        }
        return builder;
    }

    private static String firstAddress(String header) {
        try {
            jakarta.mail.internet.InternetAddress[] parsed = jakarta.mail.internet.InternetAddress.parseHeader(header,
                    false);
            return parsed.length == 0 ? null : parsed[0].getAddress();
        } catch (jakarta.mail.internet.AddressException ex) {
            return null;
        }
    }

    static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String abbreviate(String value, int max) {
        return MailService.abbreviate(value, max);
    }
}
