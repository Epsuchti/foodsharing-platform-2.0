package network.foodsharing.user.service;

import java.util.Optional;
import java.util.UUID;

/**
 * What the user module needs to know about regions (home region name, membership check), implemented by the region
 * module, so the user module does not depend on it (same pattern as {@code history.ActorDirectory}, decision 9).
 * Without an implementation (slice tests) regions are unknown.
 */
public interface RegionDirectory {

    /** Short region data; {@code type} is the {@code UnitType} name. */
    record RegionInfo(UUID id, String slug, String name, String type, UUID parentId) {
    }

    Optional<RegionInfo> find(UUID regionId);

    /**
     * Loads several regions in one query and keeps them for the rest of the current request, so that the following
     * {@link #find} calls of a list mapping cost nothing (N+1 guard, QueryCountIT).
     */
    default void prefetch(java.util.Collection<UUID> regionIds) {
    }

    /** Active member of the region. */
    boolean isActiveMember(UUID userId, UUID regionId);

    /** A region type members join directly (PART_OF_TOWN, CITY, REGION, DISTRICT). */
    boolean isAccessible(UUID regionId);
}
