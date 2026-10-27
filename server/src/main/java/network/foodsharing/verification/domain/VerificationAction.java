package network.foodsharing.verification.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Verification history (subject = the verified user; reason = the required message). */
public enum VerificationAction implements HistoryAction {
    VERIFIED,
    UNVERIFIED,
    IDENTITY_CONFIRMED,
    IDENTITY_CONFIRMATION_REVERTED;

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
