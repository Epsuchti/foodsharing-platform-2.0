package network.foodsharing.fsp.domain;

/** Why a user may not sign up for a cleaning shift (FS2, checked in this order). */
public enum CleaningEligibilityReason {
    NOT_VERIFIED,
    SLEEPING,
    SHIFT_IN_PAST,
    NO_FREE_SLOT,
    ALREADY_SIGNED_UP
}
