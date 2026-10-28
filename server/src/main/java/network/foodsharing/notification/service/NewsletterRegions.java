package network.foodsharing.notification.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Region data the newsletters need (N7), implemented by the region module (region depends on notification, not the
 * other way round).
 */
public interface NewsletterRegions {

    /** A region as shown on a newsletter. */
    record RegionSummary(UUID id, String slug, String name, String type, UUID parentId) {
    }

    Optional<RegionSummary> find(UUID regionId);

    /** Active members of the region and all regions / groups below it. */
    List<UUID> memberIdsOfSubtree(UUID regionId);

    /** Regions / groups the user is ambassador (admin) of. */
    Set<UUID> adminRegionIds(UUID userId);
}
