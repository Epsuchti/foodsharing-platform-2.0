package network.foodsharing.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one entry point for notifications before M4 (docs/conventions.md A12): a bell per recipient and, unless
 * {@code bellOnly}, a mail through the outbox. Inactive or unknown recipients are skipped. Cross-module callers
 * trigger this from {@code @TransactionalEventListener(AFTER_COMMIT)} listeners, never from the mutating service.
 */
@Service
public class NotificationService {

    private final BellService bells;
    private final MailOutboxService mails;
    private final UserService users;
    private final HistoryService history;
    private final AppProperties properties;

    public NotificationService(BellService bells, MailOutboxService mails, UserService users, HistoryService history,
                               AppProperties properties) {
        this.bells = bells;
        this.mails = mails;
        this.users = users;
        this.history = history;
        this.properties = properties;
    }

    /** @return the created bells (one per active recipient) */
    @Transactional
    public List<Bell> notify(NotificationRequest request) {
        List<Bell> created = new java.util.ArrayList<>();
        for (UUID recipientId : request.recipientIds()) {
            Optional<User> recipient = users.findActive(recipientId);
            if (recipient.isEmpty()) {
                continue;
            }
            UUID mailId = null;
            if (!request.bellOnly() && request.mailTemplate() != null) {
                Map<String, Object> variables = new HashMap<>(request.params());
                variables.put("link", request.link() == null ? null : absolute(request.link()));
                variables.put("displayName", recipient.get().getDisplayName());
                mailId = mails.enqueue(new MailDraft(recipient.get().getEmail(), request.mailTemplate(),
                        recipient.get().getPreferredLanguage(), variables));
            }
            created.add(bells.create(recipientId, request.type(), request.params(), request.link(),
                    request.identifier(), request.icon(), mailId));
        }
        history.record(history.event(NotificationAction.SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("type", request.type().name(), "recipients", created.size(),
                        "mailed", !request.bellOnly() && request.mailTemplate() != null))
                .build());
        return created;
    }

    /** Removes bells created with this identifier. */
    @Transactional
    @NoHistory(reason = "bells are transient hints; the action that resolves them records its own history")
    public int removeBells(String identifier) {
        return bells.deleteByIdentifier(identifier);
    }

    private String absolute(String link) {
        return link.startsWith("http") ? link : properties.getBaseUrl() + link;
    }
}
