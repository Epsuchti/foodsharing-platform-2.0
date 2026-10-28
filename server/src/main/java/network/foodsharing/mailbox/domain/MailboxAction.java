package network.foodsharing.mailbox.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Mailbox history (subject MAILBOX; docs/conventions.md A35). */
public enum MailboxAction implements HistoryAction {
    MAILBOX_CREATED,
    MAILBOX_DELETED,
    EMAIL_NAME_CHANGED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    MAIL_SENT,
    MAIL_RECEIVED,
    MAIL_MOVED,
    MAIL_DELETED,
    BOUNCE_RECORDED,
    UNKNOWN_RECIPIENT,
    FIELD_CHANGED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.MAILBOX;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
