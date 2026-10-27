package network.foodsharing.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scoped roles and region permissions (plan §9 layers 2 and 3, docs/conventions.md A10). Implemented by the region
 * module ({@code region.service.RegionPermissionResolver}); {@link PermissionService} falls back to
 * {@link NoopScopedPermissionResolver} when no implementation is present (slice tests).
 *
 * <p>The resolver answers for plain user ids and knows nothing about ORGA: the global ORGA override is applied by
 * {@link PermissionService} before it asks the resolver.
 */
public interface ScopedPermissionResolver {

    /** Active member of the region or working group. */
    boolean isMember(UUID userId, UUID regionOrGroupId);

    /** Ambassador of the region / admin of the working group (one table {@code region_admin}). */
    boolean isAdmin(UUID userId, UUID regionOrGroupId);

    boolean isAdminOfAny(UUID userId);

    Set<UUID> adminOf(UUID userId);

    Set<UUID> memberOf(UUID userId);

    /**
     * Function rights in a region (upstream {@code RegionPermissions::hasFunctionGroupPermissionForRegion}): when the
     * region has a working group for the function, its admins; otherwise the ambassadors of the region.
     */
    boolean hasFunctionGroupPermission(UUID userId, WorkingGroupFunction function, UUID regionId);

    /** A direct {@code region_permission} row or a grant through a working-group function (A10 table). */
    boolean hasRegionPermission(UUID userId, Permission permission, UUID regionId);

    /** Every region permission of the user, by region (direct and through functions). */
    Map<UUID, Set<Permission>> permissionsByRegion(UUID userId);

    /** Ambassador of the region, or admin of its FSMANAGEMENT group (upstream {@code mayAdministrateRegion}). */
    boolean mayAdministrateRegion(UUID actorId, UUID regionId);

    /**
     * The actor administrates a region the user is a member of: ambassador of it or admin of its FSMANAGEMENT group
     * (upstream {@code CommonPermissions::mayAdministrateRegion} over the user's regions).
     */
    boolean mayAdministrateUser(UUID actorId, UUID userId);
}
