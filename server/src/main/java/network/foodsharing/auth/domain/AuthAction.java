package network.foodsharing.auth.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** History actions of the auth module. Subject = the user, except REGISTRATION_BLOCKED (SYSTEM: no user exists). */
public enum AuthAction implements HistoryAction {
    LOGIN(SubjectType.USER, RetentionPolicy.YEARS_2),
    LOGOUT(SubjectType.USER, RetentionPolicy.YEARS_2),
    LOGIN_FAILED(SubjectType.USER, RetentionPolicy.DAYS_90),
    SESSION_REVOKED(SubjectType.USER, RetentionPolicy.YEARS_2),
    SESSIONS_REVOKED_ALL(SubjectType.USER, RetentionPolicy.YEARS_2),
    ACCOUNT_LINKED(SubjectType.USER, RetentionPolicy.ACCOUNT_LIFETIME),
    METHOD_REMOVED(SubjectType.USER, RetentionPolicy.ACCOUNT_LIFETIME),
    PASSKEY_REGISTERED(SubjectType.USER, RetentionPolicy.ACCOUNT_LIFETIME),
    PASSKEY_DELETED(SubjectType.USER, RetentionPolicy.ACCOUNT_LIFETIME),
    PASSKEY_RENAMED(SubjectType.USER, RetentionPolicy.ACCOUNT_LIFETIME),
    MAGIC_LINK_REQUESTED(SubjectType.USER, RetentionPolicy.DAYS_90),
    REGISTRATION_BLOCKED(SubjectType.SYSTEM, RetentionPolicy.YEARS_2);

    private final SubjectType subjectType;
    private final RetentionPolicy retention;

    AuthAction(SubjectType subjectType, RetentionPolicy retention) {
        this.subjectType = subjectType;
        this.retention = retention;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return subjectType;
    }

    @Override
    public RetentionPolicy retention() {
        return retention;
    }
}
