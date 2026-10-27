package network.foodsharing.user.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** History actions of the user module (subject = the user). */
public enum UserAction implements HistoryAction {
    ACCOUNT_CREATED,
    SETTINGS_CHANGED,
    FIELD_CHANGED,
    ROLE_CHANGED,
    DEACTIVATED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.USER;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.ACCOUNT_LIFETIME;
    }
}
