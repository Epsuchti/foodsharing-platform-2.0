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
    SETTINGS_CHANGED;

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
