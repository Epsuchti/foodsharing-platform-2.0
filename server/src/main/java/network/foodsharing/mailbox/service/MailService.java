package network.foodsharing.mailbox.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.realtime.RealtimeEvent;
import network.foodsharing.common.realtime.RealtimeEventType;
import network.foodsharing.common.realtime.RealtimePublisher;
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
import network.foodsharing.mailbox.repository.MailAttachmentRepository;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.notification.mail.service.DirectMail;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mails in a mailbox (MB2–MB4; A35): folders and search, read/unread, move, delete (TRASH → permanent), compose,
 * reply and forward through the mail outbox from the mailbox address, unread counts and realtime updates.
 */
@Service
public class MailService {

    static final int MAX_RECIPIENTS = 100;
    static final int MAX_ATTACHMENTS = 10;
    static final int MAX_SUBJECT = 998;

    /** A composed mail (API {@code MailCompose}). */
    public record Compose(List<String> to, List<String> cc, List<String> bcc, String subject, String bodyHtml,
                          List<UUID> attachmentUploadIds, UUID inReplyToMailId, UUID forwardOfMailId) {
    }

    private final MailMessageRepository mails;
    private final MailAttachmentRepository attachments;
    private final MailboxAccessService access;
    private final MailboxService mailboxes;
    private final MailAttachmentStore attachmentStore;
    private final MailHtmlSanitizer sanitizer;
    private final UploadService uploads;
    private final MailOutboxService outbox;
    private final RealtimePublisher realtime;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public MailService(MailMessageRepository mails, MailAttachmentRepository attachments, MailboxAccessService access,
                       MailboxService mailboxes, MailAttachmentStore attachmentStore, MailHtmlSanitizer sanitizer,
                       UploadService uploads, MailOutboxService outbox, RealtimePublisher realtime,
                       HistoryService history, AppProperties properties, Clock clock) {
        this.mails = mails;
        this.attachments = attachments;
        this.access = access;
        this.mailboxes = mailboxes;
        this.attachmentStore = attachmentStore;
        this.sanitizer = sanitizer;
        this.uploads = uploads;
        this.outbox = outbox;
        this.realtime = realtime;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------- queries

    /** A folder page, newest first; {@code q} searches subject, sender, recipients and text (case-insensitive). */
    @Transactional(readOnly = true)
    public Page<MailMessage> page(SessionPrincipal actor, UUID mailboxId, MailFolder folder, int page, int size,
                                  String q) {
        access.requireReadable(actor, mailboxId);
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        String pattern = q == null || q.isBlank() ? null : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        return mails.search(mailboxId, folder, pattern, PageRequest.of(page, size));
    }

    /** Unread INBOX mails per mailbox (0 when none). */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> unreadCounts(Collection<UUID> mailboxIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        mailboxIds.forEach(id -> counts.put(id, 0));
        if (!mailboxIds.isEmpty()) {
            for (Object[] row : mails.unreadCounts(mailboxIds, MailFolder.INBOX)) {
                counts.put((UUID) row[0], ((Number) row[1]).intValue());
            }
        }
        return counts;
    }

    /** Navigation badge: unread INBOX mails over every mailbox the user reads. */
    @Transactional(readOnly = true)
    public int totalUnread(SessionPrincipal actor) {
        List<UUID> ids = access.visibleMailboxes(actor).stream().map(Mailbox::getId).toList();
        return unreadCounts(ids).values().stream().mapToInt(Integer::intValue).sum();
    }

    @Transactional(readOnly = true)
    public List<MailAttachment> attachmentsOf(UUID mailId) {
        return attachments.findByMailIdOrderByCreatedAtAsc(mailId);
    }

    /** @throws ApiException 404 ATTACHMENT_NOT_FOUND (+ mail access codes) */
    @Transactional(readOnly = true)
    public MailAttachment requireAttachment(SessionPrincipal actor, UUID mailId, UUID attachmentId) {
        access.requireReadableMail(actor, mailId);
        return attachments.findByIdAndMailId(attachmentId, mailId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.ATTACHMENT_NOT_FOUND));
    }

    // ------------------------------------------------------------------------------------- read / move

    /** Opens a mail; marks it read unless {@code keepUnread}. */
    @Transactional
    @NoHistory(reason = "read state is a per-mailbox flag, not an action (A35)")
    public MailMessage open(SessionPrincipal actor, UUID mailId, boolean keepUnread) {
        MailMessage mail = access.requireReadableMail(actor, mailId);
        Mailbox mailbox = access.require(mail.getMailboxId());
        Instant now = clock.instant();
        mailbox.setLastAccessAt(now);
        if (!keepUnread && mail.getReadAt() == null) {
            mail.setReadAt(now);
            publish(mailbox, mail, "READ");
        }
        return mail;
    }

    /**
     * Sets read/unread and/or moves the mail to another folder (moving remembers the previous folder).
     *
     * @throws ApiException 404 MAILBOX_MAIL_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional
    public MailMessage update(SessionPrincipal actor, UUID mailId, Boolean read, MailFolder folder) {
        MailMessage mail = access.requireReadableMail(actor, mailId);
        Mailbox mailbox = access.require(mail.getMailboxId());
        boolean changed = false;
        if (read != null && read != (mail.getReadAt() != null)) {
            mail.setReadAt(read ? clock.instant() : null);
            changed = true;
        }
        if (folder != null && folder != mail.getFolder()) {
            move(mailbox, mail, folder);
            changed = true;
        }
        if (changed) {
            publish(mailbox, mail, "UPDATED");
        }
        return mail;
    }

    /** Outside TRASH: moves the mail to TRASH; in TRASH: deletes it with its attachments. */
    @Transactional
    public void delete(SessionPrincipal actor, UUID mailId) {
        MailMessage mail = access.requireReadableMail(actor, mailId);
        Mailbox mailbox = access.require(mail.getMailboxId());
        if (mail.getFolder() != MailFolder.TRASH) {
            move(mailbox, mail, MailFolder.TRASH);
        } else {
            attachmentStore.deleteAll(attachments.findByMailIdOrderByCreatedAtAsc(mailId));
            mails.delete(mail);
            history.record(event(MailboxAction.MAIL_DELETED, mailbox).metadata("mailId", mailId.toString())
                    .metadata("subject", abbreviate(mail.getSubject(), 200)).build());
        }
        publish(mailbox, mail, "UPDATED");
    }

    private void move(Mailbox mailbox, MailMessage mail, MailFolder folder) {
        history.record(event(MailboxAction.MAIL_MOVED, mailbox).metadata("mailId", mail.getId().toString())
                .change("folder", mail.getFolder(), folder).build());
        mail.setPreviousFolder(mail.getFolder());
        mail.setFolder(folder);
    }

    // ------------------------------------------------------------------------------------------ compose

    /**
     * Sends a mail from the mailbox address through the outbox (one outbox mail per recipient) and stores the SENT
     * copy. Reply sets {@code answered_at} on the original and {@code In-Reply-To}/{@code References}; forward adds
     * the original's attachments.
     *
     * @throws ApiException 400 INVALID_RECIPIENT / TOO_MANY_RECIPIENTS / TOO_MANY_ATTACHMENTS / ATTACHMENT_TOO_LARGE
     *                      / VALIDATION_FAILED, 403 ACCESS_DENIED, 404 MAILBOX_NOT_FOUND / MAILBOX_MAIL_NOT_FOUND /
     *                      UPLOAD_NOT_FOUND, 429 MAILBOX_RATE_LIMITED, 503 MAILBOX_DOMAIN_NOT_CONFIGURED
     */
    @Transactional
    public MailMessage send(SessionPrincipal actor, UUID mailboxId, Compose compose) {
        Mailbox mailbox = access.requireReadable(actor, mailboxId);
        String domain = properties.getMailbox().getDomain();
        if (domain == null || domain.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.MAILBOX_DOMAIN_NOT_CONFIGURED);
        }
        Instant now = clock.instant();
        List<MailAddresses.Address> to = recipients(compose.to());
        List<MailAddresses.Address> cc = recipients(compose.cc());
        List<MailAddresses.Address> bcc = recipients(compose.bcc());
        if (to.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_RECIPIENT);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (List<MailAddresses.Address> list : List.of(to, cc, bcc)) {
            list.forEach(a -> unique.add(a.address().toLowerCase(Locale.ROOT)));
        }
        if (unique.size() > MAX_RECIPIENTS) {
            throw ApiException.badRequest(ApiErrorCode.TOO_MANY_RECIPIENTS);
        }
        String subject = compose.subject() == null ? "" : compose.subject().strip();
        if (subject.length() > MAX_SUBJECT || compose.bodyHtml() == null
                || compose.bodyHtml().length() > MailMessage.MAX_BODY) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        requireWithinRateLimit(actor.userId(), now);

        MailMessage original = null;
        if (compose.inReplyToMailId() != null) {
            original = access.requireReadableMail(actor, compose.inReplyToMailId());
        }
        MailMessage forwarded = null;
        if (compose.forwardOfMailId() != null) {
            forwarded = access.requireReadableMail(actor, compose.forwardOfMailId());
        }
        List<Upload> files = attachmentsFor(actor, compose.attachmentUploadIds(), forwarded);

        String html = sanitizer.sanitize(compose.bodyHtml());
        String text = sanitizer.toText(html);
        String messageId = "<" + UUID.randomUUID() + "@" + domain + ">";
        String fromAddress = mailboxes.address(mailbox);

        MailMessage sent = new MailMessage();
        sent.setMailboxId(mailbox.getId());
        sent.setFolder(MailFolder.SENT);
        sent.setMessageId(messageId);
        sent.setFromAddress(fromAddress);
        sent.setFromName(mailbox.getName());
        sent.setToJson(MailAddresses.toJson(to));
        sent.setCcJson(cc.isEmpty() ? null : MailAddresses.toJson(cc));
        sent.setSubject(subject);
        sent.setBodyHtml(html);
        sent.setBodyText(abbreviate(text, MailMessage.MAX_BODY));
        sent.setReceivedAt(now);
        sent.setReadAt(now);
        sent.setSentByUserId(actor.userId());
        sent.setAttachmentCount(files.size());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Message-ID", messageId);
        if (original != null && original.getMessageId() != null) {
            sent.setInReplyTo(original.getMessageId());
            headers.put("In-Reply-To", original.getMessageId());
            headers.put("References", original.getMessageId());
        }
        sent = mails.save(sent);
        for (Upload file : files) {
            attachmentStore.attach(sent.getId(), file, null);
        }
        if (original != null) {
            original.setAnsweredAt(now);
        }
        List<UUID> uploadIds = files.stream().map(Upload::getId).toList();
        for (List<MailAddresses.Address> list : List.of(to, cc, bcc)) {
            for (MailAddresses.Address recipient : list) {
                outbox.enqueueDirect(new DirectMail(recipient.address(), fromAddress, mailbox.getName(), null,
                        subject, text, html, headers, uploadIds, actor.userId()));
            }
        }
        history.record(event(MailboxAction.MAIL_SENT, mailbox).metadata("mailId", sent.getId().toString())
                .metadata("recipients", unique.size()).metadata("attachments", files.size())
                .metadata("reply", original != null).metadata("forward", forwarded != null).build());
        publish(mailbox, sent, "NEW");
        return sent;
    }

    private static List<MailAddresses.Address> recipients(List<String> entries) {
        try {
            return MailAddresses.parseStrict(entries);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_RECIPIENT).withArg("address",
                    abbreviate(ex.getMessage(), 320));
        }
    }

    /** A35: at most {@code app.mailbox.max-mails-per-hour-per-user} SENT mails per user and hour. */
    void requireWithinRateLimit(UUID userId, Instant now) {
        long sentLastHour = mails.countBySentByUserIdAndFolderAndReceivedAtAfter(userId, MailFolder.SENT,
                now.minus(Duration.ofHours(1)));
        sentLastHour += mails.countBySentByUserIdAndFolderAndReceivedAtAfter(userId, MailFolder.TRASH,
                now.minus(Duration.ofHours(1)));
        if (sentLastHour >= properties.getMailbox().getMaxMailsPerHourPerUser()) {
            throw ApiException.tooManyRequests(ApiErrorCode.MAILBOX_RATE_LIMITED, Duration.ofHours(1));
        }
    }

    private List<Upload> attachmentsFor(SessionPrincipal actor, List<UUID> uploadIds, MailMessage forwarded) {
        List<Upload> files = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        if (forwarded != null) {
            for (MailAttachment attachment : attachments.findByMailIdOrderByCreatedAtAsc(forwarded.getId())) {
                if (seen.add(attachment.getUploadId())) {
                    files.add(uploads.find(attachment.getUploadId()));
                }
            }
        }
        for (UUID id : uploadIds == null ? List.<UUID>of() : uploadIds) {
            if (seen.add(id)) {
                Upload upload = uploads.find(id);
                if (!uploads.mayRead(actor.userId(), upload)) {
                    throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
                }
                files.add(upload);
            }
        }
        if (files.size() > MAX_ATTACHMENTS) {
            throw ApiException.badRequest(ApiErrorCode.TOO_MANY_ATTACHMENTS);
        }
        long total = files.stream().mapToLong(Upload::getSize).sum();
        if (total > properties.getMailbox().getMaxAttachmentTotalMb() * 1024L * 1024L) {
            throw ApiException.badRequest(ApiErrorCode.ATTACHMENT_TOO_LARGE)
                    .withArg("maxMb", String.valueOf(properties.getMailbox().getMaxAttachmentTotalMb()));
        }
        return files;
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** Realtime {@code MAILBOX_MAIL} to the readers (A32): clients refetch lists and the unread count. */
    void publish(Mailbox mailbox, MailMessage mail, String change) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mailboxId", mailbox.getId().toString());
        payload.put("mailId", mail.getId().toString());
        payload.put("folder", mail.getFolder().name());
        payload.put("change", change);
        payload.put("unreadCount", unreadCounts(List.of(mailbox.getId())).get(mailbox.getId()));
        realtime.toUsers(access.readers(mailbox), new RealtimeEvent(RealtimeEventType.MAILBOX_MAIL, clock.instant(),
                payload));
    }

    private HistoryEvent.Builder event(MailboxAction action, Mailbox mailbox) {
        HistoryEvent.Builder builder = history.event(action, mailbox.getId());
        if (mailbox.getOwnerType() == MailboxOwnerType.REGION) {
            builder.region(mailbox.getOwnerId());
        }
        return builder;
    }

    static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
