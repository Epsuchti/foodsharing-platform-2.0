package network.foodsharing.region.service;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.RegionStatistics;

/**
 * Adds module figures to the nightly region statistics (plan §13.2 R13): stores (active cooperations), pickups and
 * weights (M3), food share points and baskets (M6). {@code subtreeIds} contains the region and all regions below it.
 * M2 has no contributors; the region module fills member counts itself.
 */
public interface RegionStatisticsContributor {

    void contribute(UUID regionId, Set<UUID> subtreeIds, RegionStatistics statistics);
}
