package network.foodsharing.notification.mail.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.domain.MailAction;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
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

    public MailOutboxService(MailOutboxRepository repository, MailTemplateService templates,
                             MailDeliveryService delivery, HistoryService history, AppProperties properties,
                             Clock clock) {
        this.repository = repository;
        this.templates = templates;
        this.delivery = delivery;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** Renders the template and queues the mail for immediate delivery; records MAIL_QUEUED. */
    @Transactional
    public UUID enqueue(MailDraft draft) {
        RenderedMail rendered = templates.render(draft.template(), draft.language(), draft.variables());
        MailOutboxMessage message = new MailOutboxMessage();
        message.setToAddress(draft.to());
        message.setTemplate(draft.template());
        message.setLanguage(rendered.language());
        message.setSubject(StringUtils.abbreviate(rendered.subject(), 500));
        message.setTextBody(StringUtils.abbreviate(rendered.textBody(), MailOutboxMessage.MAX_BODY_LENGTH));
        message.setHtmlBody(rendered.htmlBody() == null ? null
                : StringUtils.abbreviate(rendered.htmlBody(), MailOutboxMessage.MAX_BODY_LENGTH));
        message.setStatus(MailStatus.PENDING);
        message.setAttempts(0);
        message.setNextAttemptAt(clock.instant());
        MailOutboxMessage saved = repository.save(message);
        history.record(history.event(MailAction.QUEUED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("mailId", saved.getId().toString(), "template", draft.template()))
                .build());
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
}
