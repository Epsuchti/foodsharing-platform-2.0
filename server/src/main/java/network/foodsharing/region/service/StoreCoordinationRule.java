package network.foodsharing.region.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Who coordinates the stores of a region (upstream {@code mayEditStore} for the region; stores import D16.2,
 * {@code RegionDetail.mayCoordinateStores}): ORGA; else, when the region has a STORES_COORDINATION group, that
 * group's admins only; else the ambassadors of the region or one of its ancestors. Shared by the store module
 * ({@code StorePermissionService.mayCoordinateRegionStores}) and the region page so both answer the same.
 */
public final class StoreCoordinationRule {

    private StoreCoordinationRule() {
    }

    /**
     * @param coordinationGroupId the region's STORES_COORDINATION group, or null
     * @param regionChain         the region and its ancestors (only read without a coordination group)
     * @param isAdmin             whether the actor is admin (ambassador) of a region or group
     */
    public static boolean mayCoordinate(boolean orga, UUID coordinationGroupId, Supplier<List<UUID>> regionChain,
                                        Predicate<UUID> isAdmin) {
        if (orga) {
            return true;
        }
        if (coordinationGroupId != null) {
            return isAdmin.test(coordinationGroupId);
        }
        return regionChain.get().stream().anyMatch(isAdmin);
    }
}
