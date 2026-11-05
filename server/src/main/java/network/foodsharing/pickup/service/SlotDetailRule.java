package network.foodsharing.pickup.service;

/** PU3 slot dialog (decision 165): who may load the rich details of a sign-up. */
public final class SlotDetailRule {

    private SlotDetailRule() {
    }

    /** Managers (confirm or edit pickups) and people signed up for the same pickup. */
    public static boolean mayView(boolean mayConfirmPickups, boolean mayEditPickups, boolean callerSignedUpForPickup) {
        return mayConfirmPickups || mayEditPickups || callerSignedUpForPickup;
    }
}
