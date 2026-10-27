package network.foodsharing.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** No scoped roles at all: used by {@link PermissionService} when no resolver bean exists (slice tests). */
public class NoopScopedPermissionResolver implements ScopedPermissionResolver {

    @Override
    public boolean isMember(UUID userId, UUID regionOrGroupId) {
        return false;
    }

    @Override
    public boolean isAdmin(UUID userId, UUID regionOrGroupId) {
        return false;
    }

    @Override
    public boolean isAdminOfAny(UUID userId) {
        return false;
    }

    @Override
    public Set<UUID> adminOf(UUID userId) {
        return Set.of();
    }

    @Override
    public Set<UUID> memberOf(UUID userId) {
        return Set.of();
    }

    @Override
    public boolean hasFunctionGroupPermission(UUID userId, WorkingGroupFunction function, UUID regionId) {
        return false;
    }

    @Override
    public boolean hasRegionPermission(UUID userId, Permission permission, UUID regionId) {
        return false;
    }

    @Override
    public Map<UUID, Set<Permission>> permissionsByRegion(UUID userId) {
        return Map.of();
    }

    @Override
    public boolean mayAdministrateRegion(UUID actorId, UUID regionId) {
        return false;
    }

    @Override
    public boolean mayAdministrateUser(UUID actorId, UUID userId) {
        return false;
    }
}
