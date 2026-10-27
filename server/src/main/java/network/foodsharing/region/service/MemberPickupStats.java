package network.foodsharing.region.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** SPI (A22): pickup numbers of a member (region member list); implemented by the pickup module. */
public interface MemberPickupStats {

    Optional<Instant> lastPickupAt(UUID userId);

    int pickupCount(UUID userId);
}
