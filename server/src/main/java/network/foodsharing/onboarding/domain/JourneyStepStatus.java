package network.foodsharing.onboarding.domain;

/** State of a journey step. */
public enum JourneyStepStatus {
    LOCKED,
    AVAILABLE,
    IN_PROGRESS,
    WAITING_FOR_OTHERS,
    DONE;
}
