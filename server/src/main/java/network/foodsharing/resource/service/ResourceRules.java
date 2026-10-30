package network.foodsharing.resource.service;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.resource.domain.Resource;

/**
 * Pure permission rules of the resource mosaic (upstream {@code ResourcePermissions}, C6). Resources exist in cities,
 * big cities, parts of town and working groups; members of such a unit see its resources.
 */
public final class ResourceRules {

    private ResourceRules() {
    }

    /** {@link UnitType#hasResources()}: the same rule the region page reads as {@code RegionDetail.resourcesAvailable}. */
    public static boolean hasResources(UnitType type) {
        return type != null && type.hasResources();
    }

    public static boolean maySee(UnitType type, boolean member) {
        return hasResources(type) && member;
    }

    public static boolean mayAdd(long ownCount, int maxPerUser) {
        return ownCount < maxPerUser;
    }

    /** Owners edit their resources; commons are edited by the region's RESOURCES function group (or ORGA). */
    public static boolean mayEdit(Resource resource, UUID actorId, boolean mayEditCommons) {
        return resource.isCommons() ? mayEditCommons : actorId.equals(resource.getOwnerId());
    }

    /** Owner or ORGA; commons additionally by the RESOURCES function group. */
    public static boolean mayDelete(Resource resource, UUID actorId, boolean orga, boolean mayEditCommons) {
        if (orga) {
            return true;
        }
        return resource.isCommons() ? mayEditCommons : actorId.equals(resource.getOwnerId());
    }

    /** A private resource is shown only to its owner and the owner's confirmed buddies (upstream). */
    public static boolean visibleTo(Resource resource, UUID viewerId, Set<UUID> buddyIds) {
        return resource.isCommons() || !resource.isPrivateResource() || viewerId.equals(resource.getOwnerId())
                || buddyIds.contains(resource.getOwnerId());
    }

    /** Restricting a member's resource to a region needs membership of a region that has resources. */
    public static boolean mayRestrictTo(UnitType type, boolean member) {
        return maySee(type, member);
    }
}
