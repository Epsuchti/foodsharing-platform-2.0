package network.foodsharing.notification.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.user.domain.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Mail through the outbox, only for requests naming a template and not bell-only (A12/A23). */
@Component
@Order(1)
public class MailChannel implements NotificationChannel {

    private final MailOutboxService mails;
    private final AppProperties properties;

    public MailChannel(MailOutboxService mails, AppProperties properties) {
        this.mails = mails;
        this.properties = properties;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.MAIL;
    }

    @Override
    public boolean applies(NotificationRequest request) {
        return !request.bellOnly() && request.mailTemplate() != null;
    }

    @Override
    public boolean available(User recipient) {
        return recipient.getEmail() != null;
    }

    @Override
    public void deliver(User recipient, Bell bell, NotificationRequest request) {
        Map<String, Object> variables = new HashMap<>(request.params());
        variables.put("link", request.link() == null ? null : absolute(request.link()));
        variables.put("displayName", recipient.getDisplayName());
        UUID mailId = mails.enqueue(new MailDraft(recipient.getEmail(), request.mailTemplate(),
                recipient.getPreferredLanguage(), variables, recipient.getId(), request.type().category().name(),
                request.identifier()));
        bell.setMailOutboxId(mailId);
    }

    private String absolute(String link) {
        return link.startsWith("http") ? link : properties.getBaseUrl() + link;
    }
}
