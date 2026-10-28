package network.foodsharing.store.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.store.domain.FreeSlotStatus;

/**
 * SPI implemented by the pickup module (store must not depend on pickup): slot state for store lists, member pickup
 * numbers for the team list, deleting future sign-ups when someone leaves, and the delete guard. A no-op default is
 * used until the pickup module provides a bean.
 */
public interface StorePickupInfo {

    record SlotInfo(Instant nextPickupAt, FreeSlotStatus freeSlotStatus, int freeSlotCount) {
    }

    record MemberStats(int pickupCount, Instant firstPickupAt, Instant lastPickupAt) {
    }

    /** A confirmed sign-up (store statistics). */
    record PastSignup(UUID userId, Instant pickupAt) {
    }

    Map<UUID, SlotInfo> slotInfo(Collection<UUID> storeIds);

    Map<UUID, MemberStats> memberStats(UUID storeId);

    /** Removes the user's sign-ups for future pickups of the store; returns how many. */
    int deleteFutureSignups(UUID storeId, UUID userId);

    /** Did the store ever have a sign-up (delete guard)? */
    boolean hasSignups(UUID storeId);

    /** Confirmed sign-ups of the store with {@code from <= pickupAt < to}. */
    default java.util.List<PastSignup> confirmedSignups(UUID storeId, Instant from, Instant to) {
        return java.util.List.of();
    }
}
