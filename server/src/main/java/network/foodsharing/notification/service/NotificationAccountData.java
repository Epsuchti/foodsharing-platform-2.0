package network.foodsharing.notification.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of the notification module (A40): bells, notification settings, push subscriptions and
 * newsletter recipient rows are deleted (the Telegram chat id lives on the user row and is cleared there).
 */
@Component
public class NotificationAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public NotificationAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "Bell", "userId", userId);
        UserRows.delete(em, "UserNotificationSetting", "userId", userId);
        UserRows.delete(em, "PushSubscription", "userId", userId);
        UserRows.delete(em, "NewsletterRecipient", "userId", userId);
        UserRows.delete(em, "TelegramLinkToken", "userId", userId);
    }

    @Override
    public String section() {
        return "notifications";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bells", UserRows.read(em, "Bell", "userId", userId, "type", "titleKey", "params", "link",
                "createdAt", "readAt", "deletedAt"));
        result.put("settings", UserRows.read(em, "UserNotificationSetting", "userId", userId, "category", "mail",
                "push", "telegram"));
        result.put("pushSubscriptions", UserRows.read(em, "PushSubscription", "userId", userId, "userAgent",
                "createdAt", "lastSuccessAt"));
        return result;
    }
}
