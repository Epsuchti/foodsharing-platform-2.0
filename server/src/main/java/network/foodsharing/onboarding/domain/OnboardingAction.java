package network.foodsharing.onboarding.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Onboarding journey history (subject = the newcomer). */
public enum OnboardingAction implements HistoryAction {
    CHAPTER_READ,
    REGION_STEP_CONFIRMED,
    NUDGE_SENT,
    JOURNEY_COMPLETED;

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
