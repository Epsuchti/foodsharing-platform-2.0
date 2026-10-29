package network.foodsharing.region.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** History actions of working groups (subject = the group; secondary subject = the affected user). */
public enum GroupAction implements HistoryAction {
    CREATED,
    UPDATED,
    FIELD_CHANGED,
    ARCHIVED,
    MEMBER_JOINED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    ADMIN_GRANTED,
    ADMIN_REVOKED,
    APPLICATION_SUBMITTED,
    APPLICATION_ACCEPTED,
    APPLICATION_DECLINED,
    APPLICATION_CANCELLED,
    MAIL_SENT;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.GROUP;
    }

    @Override
    public RetentionPolicy retention() {
        // group mails are communication logs, kept like mailbox mails (D17.3)
        return this == MAIL_SENT ? RetentionPolicy.YEARS_2 : RetentionPolicy.FOREVER;
    }
}
