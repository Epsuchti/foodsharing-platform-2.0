package network.foodsharing.pickup.domain;

/** Why a user may not sign up for a pickup (checked in this order). */
public enum PickupEligibilityReason {
    NOT_TEAM_MEMBER,
    JUMPER_CANNOT_PICKUP,
    NOT_VERIFIED,
    HYGIENE_CERTIFICATE_REQUIRED,
    PHONE_REQUIRED,
    SLEEPING,
    PICKUP_IN_PAST,
    SIGNUP_NOT_YET_OPEN,
    NO_FREE_SLOT,
    ALREADY_SIGNED_UP,
    REGION_RULE_TIMESPAN_LIMIT,
    REGION_RULE_DAY_LIMIT
}
