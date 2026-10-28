package network.foodsharing.region.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** SPI (A22): pickup numbers of a member (region member list); implemented by the pickup module. */
public interface MemberPickupStats {

    Optional<Instant> lastPickupAt(UUID userId);

    int pickupCount(UUID userId);

    /** Batch form of {@link #lastPickupAt(UUID)} for member lists (users without a pickup are missing). */
    default java.util.Map<UUID, Instant> lastPickupAt(java.util.Collection<UUID> userIds) {
        java.util.Map<UUID, Instant> result = new java.util.HashMap<>();
        userIds.forEach(id -> lastPickupAt(id).ifPresent(at -> result.put(id, at)));
        return result;
    }
}
