package network.foodsharing.notification.mail.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/**
 * History of the mail outbox (subject SYSTEM, metadata {mailId, template}; recipient addresses are not recorded).
 */
public enum MailAction implements HistoryAction {
    QUEUED, SENT, FAILED;

    @Override
    public String code() {
        return "MAIL_" + name();
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
