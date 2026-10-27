package network.foodsharing.verification.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Passport history (subject = the user; secondary subject = the region for batch generation). */
public enum PassportAction implements HistoryAction {
    PASSPORT_GENERATED,
    PASSPORT_GENERATED_BY_AMBASSADOR,
    PASSPORT_REQUESTED,
    WALLET_PASS_CREATED;

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
