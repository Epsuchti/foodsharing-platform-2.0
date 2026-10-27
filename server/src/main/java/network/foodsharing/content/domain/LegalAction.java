package network.foodsharing.content.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Consent history (subject = the user; metadata kind + version). */
public enum LegalAction implements HistoryAction {
    CONSENT_GIVEN,
    CONSENT_REQUIRED_AGAIN;

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
