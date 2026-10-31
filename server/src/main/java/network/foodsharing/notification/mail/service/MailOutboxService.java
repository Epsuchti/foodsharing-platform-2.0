package network.foodsharing.notification.mail.service;

import java.time.Instant;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.notification.domain.NotificationAction;
import org.springframework.data.domain.Page;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.domain.MailAction;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.config.AppProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional mail outbox: callers {@link #enqueue} inside their own transaction (so a rolled-back action never
 * sends a mail); {@code MailOutboxJob} delivers due mails via {@link MailDeliveryService}.
 */
@Service
public class MailOutboxService {

    private final MailOutboxRepository repository;
    private final MailTemplateService templates;
    private final MailDeliveryService delivery;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final MailRateLimiter rateLimiter;

    /** Bounce suppression (A18); optional so slice tests can build the service by hand. */
    private org.springframework.beans.factory.ObjectProvider<network.foodsharing.user.service.UserService> users;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setUsers(org.springframework.beans.factory.ObjectProvider<network.foodsharing.user.service.UserService> users) {
        this.users = users;
    }

    public MailOutboxService(MailOutboxRepository repository, MailTemplateService templates,
                             MailDeliveryService delivery, HistoryService history, AppProperties properties,
                             Clock clock, MailRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.repository = repository;
        this.templates = templates;
        this.delivery = delivery;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Renders the template and queues the mail; records MAIL_QUEUED. The {@link MailRateLimiter} may defer it
     * ({@code rate_limited_until}) or suppress a duplicate (NOTIFICATION_MAIL_SUPPRESSED, returns null).
     *
     * @return the outbox id, null when suppressed
     */
    @Transactional
    public UUID enqueue(MailDraft draft) {
        Instant now = clock.instant();
        MailRateLimiter.Decision decision = rateLimiter.check(draft, now);
        if (decision.outcome() == MailRateLimiter.Outcome.SUPPRESS) {
            history.record(history.event(NotificationAction.MAIL_SUPPRESSED, HistoryService.SYSTEM_SUBJECT_ID)
                    .metadata(Map.of("template", draft.template(), "identifier", draft.identifier())).build());
            return null;
        }
        if (bounced(draft)) {
            history.record(history.event(NotificationAction.MAIL_SUPPRESSED, HistoryService.SYSTEM_SUBJECT_ID)
                    .metadata(Map.of("template", draft.template(), "reason", "BOUNCED")).build());
            return null;
        }
        RenderedMail rendered = templates.render(draft.template(), draft.language(), draft.variables());
        MailOutboxMessage message = new MailOutboxMessage();
        message.setToAddress(draft.to());
        message.setTemplate(draft.template());
        message.setLanguage(rendered.language());
        message.setSubject(StringUtils.abbreviate(rendered.subject(), 500));
        Object senderRegion = draft.variables().get(RegionSenderNames.MAIL_VARIABLE);
        if (senderRegion != null && !senderRegion.toString().isBlank()) {
            // AU11: automated mail on behalf of a region, From "foodsharing <Region>" <app.mail.from address>
            message.setFromName(StringUtils.abbreviate(RegionSenderNames.fromName(senderRegion.toString()), 200));
        }
        Object unsubscribe = draft.variables().get("unsubscribeUrl");
        if (unsubscribe != null) {
            message.setHeaders("List-Unsubscribe: <" + unsubscribe + ">");
        }
        message.setTextBody(StringUtils.abbreviate(rendered.textBody(), MailOutboxMessage.MAX_BODY_LENGTH));
        message.setHtmlBody(rendered.htmlBody() == null ? null
                : StringUtils.abbreviate(rendered.htmlBody(), MailOutboxMessage.MAX_BODY_LENGTH));
        message.setStatus(MailStatus.PENDING);
        message.setAttempts(0);
        message.setUserId(draft.userId());
        message.setCategory(draft.category());
        message.setIdentifier(draft.identifier() == null ? null : StringUtils.abbreviate(draft.identifier(), 200));
        message.setPriority(MailDraft.AUTH.equals(draft.category()) ? MailOutboxMessage.PRIORITY_AUTH : 0);
        if (decision.outcome() == MailRateLimiter.Outcome.DEFER) {
            message.setRateLimitedUntil(decision.until());
            message.setNextAttemptAt(decision.until());
        } else {
            message.setNextAttemptAt(now);
        }
        MailOutboxMessage saved = repository.save(message);
        history.record(history.event(MailAction.QUEUED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("mailId", saved.getId().toString(), "template", draft.template()))
                .build());
        return saved.getId();
    }

    /**
     * Queues an already composed mail (no template, no rate limiter: the caller limits, e.g. mailbox compose A35);
     * records MAIL_QUEUED.
     *
     * @return the outbox id
     */
    @Transactional
    public UUID enqueueDirect(DirectMail mail) {
        MailOutboxMessage message = new MailOutboxMessage();
        message.setToAddress(mail.to());
        message.setTemplate(DirectMail.TEMPLATE);
        message.setLanguage(network.foodsharing.api.model.Language.DE);
        message.setSubject(StringUtils.abbreviate(mail.subject() == null ? "" : mail.subject(), 500));
        message.setTextBody(StringUtils.abbreviate(mail.textBody() == null ? "" : mail.textBody(),
                MailOutboxMessage.MAX_BODY_LENGTH));
        message.setHtmlBody(mail.htmlBody() == null ? null
                : StringUtils.abbreviate(mail.htmlBody(), MailOutboxMessage.MAX_BODY_LENGTH));
        message.setFromAddress(mail.fromAddress());
        message.setFromName(mail.fromName() == null ? null : StringUtils.abbreviate(mail.fromName(), 200));
        message.setReplyTo(mail.replyTo());
        if (!mail.headers().isEmpty()) {
            StringBuilder headers = new StringBuilder();
            mail.headers().forEach((name, value) -> headers.append(name).append(": ")
                    .append(value.replace('\n', ' ').replace('\r', ' ')).append('\n'));
            message.setHeaders(StringUtils.abbreviate(headers.toString().strip(), 4000));
        }
        if (!mail.attachmentUploadIds().isEmpty()) {
            message.setAttachmentsJson(mail.attachmentUploadIds().stream().map(id -> "\"" + id + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]")));
        }
        message.setStatus(MailStatus.PENDING);
        message.setAttempts(0);
        message.setUserId(mail.senderUserId());
        message.setCategory("MAILBOX");
        message.setNextAttemptAt(clock.instant());
        MailOutboxMessage saved = repository.save(message);
        history.record(history.event(MailAction.QUEUED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("mailId", saved.getId().toString(), "template", DirectMail.TEMPLATE)).build());
        return saved.getId();
    }

    /**
     * Delivers up to {@code app.mail.outbox-batch-size} due mails, each in its own transaction.
     *
     * @return number of mails attempted
     */
    public int processDue() {
        List<UUID> due = repository.findDueIds(MailStatus.PENDING, clock.instant(),
                PageRequest.of(0, properties.getMail().getOutboxBatchSize()));
        due.forEach(delivery::deliver);
        return due.size();
    }

    /** Outbox page for ORGA, newest first; {@code search} matches recipient, subject or template. */
    @Transactional(readOnly = true)
    public Page<MailOutboxMessage> search(MailStatus status, String search, int page, int size) {
        String pattern = search == null || search.isBlank() ? null
                : "%" + search.strip().toLowerCase(java.util.Locale.ROOT) + "%";
        return repository.search(status, pattern, PageRequest.of(page, size));
    }

    /**
     * Puts a FAILED (or still PENDING) mail back into the queue now with fresh attempts; records MAIL_RETRIED.
     *
     * @throws ApiException 404 MAIL_NOT_FOUND, 409 MAIL_NOT_RETRYABLE for sent mails
     */
    @Transactional
    public MailOutboxMessage retry(UUID mailId) {
        MailOutboxMessage message = repository.findById(mailId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.MAIL_NOT_FOUND));
        if (message.getStatus() == MailStatus.SENT) {
            throw ApiException.conflict(ApiErrorCode.MAIL_NOT_RETRYABLE);
        }
        message.setStatus(MailStatus.PENDING);
        message.setAttempts(0);
        message.setNextAttemptAt(clock.instant());
        message.setRateLimitedUntil(null);
        history.record(history.event(NotificationAction.MAIL_RETRIED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("mailId", message.getId().toString(), "template", message.getTemplate())).build());
        return message;
    }

    /** Cleanup: SENT mails older than {@code before}; @return how many were deleted. */
    @Transactional
    @NoHistory(reason = "retention housekeeping of delivered mails (cleanup job)")
    public int purgeSentBefore(Instant before) {
        return repository.deleteByStatusAndSentAtBefore(MailStatus.SENT, before);
    }

    /** A18: non-AUTH mails to a user whose current address hard-bounced are not sent. */
    private boolean bounced(MailDraft draft) {
        if (users == null || draft.userId() == null || MailDraft.AUTH.equals(draft.category())) {
            return false;
        }
        network.foodsharing.user.service.UserService service = users.getIfAvailable();
        return service != null && service.findAll(java.util.List.of(draft.userId())).stream()
                .anyMatch(u -> u.getEmailBouncedAt() != null && u.getEmail().equalsIgnoreCase(draft.to()));
    }
}
