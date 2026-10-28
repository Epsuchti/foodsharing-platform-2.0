package network.foodsharing.notification.domain;

/**
 * User-facing notification groups (A23): every {@link NotificationType} belongs to one; channel settings (mail, push,
 * Telegram) are stored per category. {@link #SYSTEM} cannot be switched off.
 */
public enum NotificationCategory {
    STORE_TEAM,
    PICKUPS,
    PICKUP_REMINDER,
    AUTOMATION,
    INTRO_PICKUPS,
    REGION,
    ONBOARDING,
    FORUM,
    MESSAGES,
    MAILBOX,
    SYSTEM;

    public boolean switchable() {
        return this != SYSTEM;
    }
}
