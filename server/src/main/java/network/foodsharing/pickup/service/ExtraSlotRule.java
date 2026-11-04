package network.foodsharing.pickup.service;

/**
 * AU6: may the earliest sign-up of a pickup add another extra slot? Pure rule, shared by the add operation and the
 * UI flags.
 */
public final class ExtraSlotRule {

    private ExtraSlotRule() {
    }

    /**
     * @param maxExtra          the store's {@code maxExtraSlots} (0 = off)
     * @param addedCount        extra slots already added to this pickup
     * @param slotCount         current slot count of the pickup
     * @param maxSlotsPerPickup global cap of slots per pickup
     * @param isFirst           the caller holds the earliest sign-up
     * @param inFuture          the pickup has not started yet
     */
    public static boolean mayAdd(int maxExtra, int addedCount, int slotCount, int maxSlotsPerPickup, boolean isFirst,
                                 boolean inFuture) {
        return maxExtra > 0 && addedCount < maxExtra && slotCount + 1 <= maxSlotsPerPickup && isFirst && inFuture;
    }
}
