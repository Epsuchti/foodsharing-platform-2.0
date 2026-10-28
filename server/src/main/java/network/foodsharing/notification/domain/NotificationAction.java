package network.foodsharing.notification.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Notification history (subject SYSTEM; metadata type + recipient id). Codes are prefixed (MailAction uses MAIL_). */
public enum NotificationAction implements HistoryAction {
    SENT,
    BELL_CREATED,
    BELL_READ,
    BELL_DELETED,
    SETTINGS_CHANGED,
    PUSH_SUBSCRIBED,
    PUSH_UNSUBSCRIBED,
    PUSH_SENT,
    TELEGRAM_LINKED,
    TELEGRAM_UNLINKED,
    TELEGRAM_SENT,
    TELEGRAM_CHAT_ADDED,
    TELEGRAM_CHAT_REMOVED,
    NEWSLETTER_CREATED,
    NEWSLETTER_UPDATED,
    NEWSLETTER_QUEUED,
    NEWSLETTER_SENT,
    NEWSLETTER_CANCELLED,
    NEWSLETTER_OPT_IN,
    NEWSLETTER_OPT_OUT,
    MAIL_RETRIED,
    MAIL_SUPPRESSED;

    @Override
    public String code() {
        return "NOTIFICATION_" + name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.SYSTEM;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.DAYS_90;
    }
}
