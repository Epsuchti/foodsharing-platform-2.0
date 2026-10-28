package network.foodsharing.intropickup.domain;

/** Status of a guide in a region's guide list (EA17). */
public enum GuideStatus {
    /** Holds CAN_GIVE_INTRO_PICKUPS, account active, not sleeping. */
    ACTIVE,
    /** Holds CAN_GIVE_INTRO_PICKUPS but is in sleep mode. */
    PAUSED,
    /** Guide right revoked, or the account is inactive. */
    DISABLED;

    public static GuideStatus of(boolean accountActive, boolean mayGiveIntroPickups, boolean sleeping) {
        if (!accountActive || !mayGiveIntroPickups) {
            return DISABLED;
        }
        return sleeping ? PAUSED : ACTIVE;
    }
}
