package network.foodsharing.support.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Support form history (subject SYSTEM, codes prefixed SUPPORT_). */
public enum SupportAction implements HistoryAction {
    TICKET_CREATED;

    @Override
    public String code() {
        return "SUPPORT_" + name();
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
