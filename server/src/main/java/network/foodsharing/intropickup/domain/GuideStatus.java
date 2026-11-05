package network.foodsharing.intropickup.domain;

/**
 * Status of a guide in a region's guide list (EA17). Since Q4 (decision 168) CAN_GIVE_INTRO_PICKUPS is the guide
 * release: granted by region administrators to people who passed the EinAb guide quiz, withdrawn at the transition
 * deadline from guides without a pass.
 */
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
