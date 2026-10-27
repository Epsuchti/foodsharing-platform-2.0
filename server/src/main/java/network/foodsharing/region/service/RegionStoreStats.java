package network.foodsharing.region.service;

import java.util.UUID;

/** SPI (A22): number of stores with an established cooperation in a region (public region page). */
public interface RegionStoreStats {

    int establishedStoreCount(UUID regionId);
}
