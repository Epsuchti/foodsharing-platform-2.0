package network.foodsharing.region.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.domain.RegionPermission;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.Permission;
import network.foodsharing.security.ScopedPermissionResolver;
import network.foodsharing.security.WorkingGroupFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scoped roles and region permissions from the region tables (docs/conventions.md A10):
 * <ul>
 *   <li>members / admins: {@code region_membership} (active) and {@code region_admin};</li>
 *   <li>function rights: the admins of the region's function group, or the ambassadors when the region has no group
 *       for the function (upstream {@code hasFunctionGroupPermissionForRegion});</li>
 *   <li>region permissions: {@code region_permission} rows plus {@code app.permissions.function-grants} for every
 *       member of a function group;</li>
 *   <li>administration: ambassador of the region or one of its ancestors, or admin of the FSMANAGEMENT group of such
 *       a region (upstream {@code mayAdministrateRegion} with parent regions; working groups excluded).</li>
 * </ul>
 * ORGA is handled by {@code PermissionService}.
 */
@Service
@Transactional(readOnly = true)
public class RegionPermissionResolver implements ScopedPermissionResolver {

    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionFunctionRepository functions;
    private final RegionPermissionRepository permissions;
    private final RegionRepository regions;
    private final RegionClosureRepository closure;
    private final AppProperties properties;

    public RegionPermissionResolver(RegionMembershipRepository memberships, RegionAdminRepository admins,
                                    RegionFunctionRepository functions, RegionPermissionRepository permissions,
                                    RegionRepository regions, RegionClosureRepository closure,
                                    AppProperties properties) {
        this.memberships = memberships;
        this.admins = admins;
        this.functions = functions;
        this.permissions = permissions;
        this.regions = regions;
        this.closure = closure;
        this.properties = properties;
    }

    @Override
    public boolean isMember(UUID userId, UUID regionOrGroupId) {
        return memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, regionOrGroupId);
    }

    @Override
    public boolean isAdmin(UUID userId, UUID regionOrGroupId) {
        return admins.existsByUserIdAndRegionId(userId, regionOrGroupId);
    }

    @Override
    public boolean isAdminOfAny(UUID userId) {
        return admins.existsByUserId(userId);
    }

    @Override
    public Set<UUID> adminOf(UUID userId) {
        return Set.copyOf(admins.findRegionIds(userId));
    }

    @Override
    public Set<UUID> memberOf(UUID userId) {
        return Set.copyOf(memberships.findActiveRegionIds(userId));
    }

    @Override
    public boolean hasFunctionGroupPermission(UUID userId, WorkingGroupFunction function, UUID regionId) {
        Optional<RegionFunction> group = functions.findByRegionIdAndFunction(regionId, function);
        return group.map(assignment -> admins.existsByUserIdAndRegionId(userId, assignment.getGroupId()))
                .orElseGet(() -> admins.existsByUserIdAndRegionId(userId, regionId));
    }

    @Override
    public boolean hasRegionPermission(UUID userId, Permission permission, UUID regionId) {
        if (permissions.existsByUserIdAndRegionIdAndPermission(userId, regionId, permission)) {
            return true;
        }
        return functionGrants(userId).getOrDefault(regionId, Set.of()).contains(permission);
    }

    @Override
    public Map<UUID, Set<Permission>> permissionsByRegion(UUID userId) {
        Map<UUID, Set<Permission>> result = new HashMap<>();
        for (RegionPermission grant : permissions.findByUserId(userId)) {
            result.computeIfAbsent(grant.getRegionId(), id -> EnumSet.noneOf(Permission.class)).add(grant.getPermission());
        }
        functionGrants(userId).forEach((regionId, granted) ->
                result.computeIfAbsent(regionId, id -> EnumSet.noneOf(Permission.class)).addAll(granted));
        return result;
    }

    @Override
    public boolean mayAdministrateRegion(UUID actorId, UUID regionId) {
        Set<UUID> scopes = administratedScopes(actorId);
        return !scopes.isEmpty() && closure.ancestors(regionId).stream().anyMatch(scopes::contains);
    }

    @Override
    public boolean mayAdministrateUser(UUID actorId, UUID userId) {
        Set<UUID> scopes = administratedScopes(actorId);
        if (scopes.isEmpty()) {
            return false;
        }
        Set<UUID> userRegions = memberships.findByUserIdAndActiveTrue(userId).stream()
                .map(RegionMembership::getRegionId).collect(Collectors.toSet());
        return closure.ancestorsOfAll(userRegions).stream().anyMatch(scopes::contains);
    }

    /**
     * Users holding a region permission in the region: direct grants plus active members of function groups whose
     * function grants it (ORGA is not included; callers add it where it matters).
     */
    public Set<UUID> holders(Permission permission, UUID regionId) {
        Set<UUID> result = new java.util.HashSet<>();
        permissions.findByRegionId(regionId).stream().filter(grant -> grant.getPermission() == permission)
                .forEach(grant -> result.add(grant.getUserId()));
        Map<WorkingGroupFunction, List<Permission>> grants = properties.getPermissions().getFunctionGrants();
        for (RegionFunction assignment : functions.findByRegionId(regionId)) {
            if (grants.getOrDefault(assignment.getFunction(), List.of()).contains(permission)) {
                result.addAll(memberships.findActiveUserIds(assignment.getGroupId()));
            }
        }
        return result;
    }

    @Override
    public Set<UUID> administrableUsers(UUID actorId, Collection<UUID> userIds) {
        Set<UUID> scopes = administratedScopes(actorId);
        if (scopes.isEmpty() || userIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> subtrees = new HashSet<>();
        scopes.forEach(scope -> subtrees.addAll(closure.subtree(scope)));
        return memberships.findActiveUserIdsAmong(subtrees, userIds);
    }

    /**
     * The region permissions of several users in one region (batch form of {@link #permissionsByRegion} for member
     * lists): direct grants plus grants through function groups assigned to that region.
     */
    public Map<UUID, Set<Permission>> permissionsInRegion(UUID regionId, Collection<UUID> userIds) {
        Map<UUID, Set<Permission>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        Set<UUID> wanted = new HashSet<>(userIds);
        for (RegionPermission grant : permissions.findByRegionId(regionId)) {
            if (wanted.contains(grant.getUserId())) {
                result.computeIfAbsent(grant.getUserId(), id -> EnumSet.noneOf(Permission.class))
                        .add(grant.getPermission());
            }
        }
        Map<WorkingGroupFunction, List<Permission>> grants = properties.getPermissions().getFunctionGrants();
        for (RegionFunction assignment : functions.findByRegionId(regionId)) {
            List<Permission> granted = grants.getOrDefault(assignment.getFunction(), List.of());
            if (granted.isEmpty()) {
                continue;
            }
            for (UUID member : memberships.findActiveUserIdsAmong(List.of(assignment.getGroupId()), wanted)) {
                result.computeIfAbsent(member, id -> EnumSet.noneOf(Permission.class)).addAll(granted);
            }
        }
        return result;
    }

    /** Permissions granted through function groups the user is an active member of, by region. */
    Map<UUID, Set<Permission>> functionGrants(UUID userId) {
        Set<UUID> memberGroups = memberships.findActiveRegionIds(userId);
        if (memberGroups.isEmpty()) {
            return Map.of();
        }
        Map<WorkingGroupFunction, List<Permission>> grants = properties.getPermissions().getFunctionGrants();
        Map<UUID, Set<Permission>> result = new HashMap<>();
        for (RegionFunction assignment : functions.findByGroupIdIn(memberGroups)) {
            List<Permission> granted = grants.getOrDefault(assignment.getFunction(), List.of());
            if (!granted.isEmpty()) {
                result.computeIfAbsent(assignment.getRegionId(), id -> EnumSet.noneOf(Permission.class)).addAll(granted);
            }
        }
        return result;
    }

    /**
     * Regions (no working groups) the actor administrates directly: ambassador of the region, or admin of the
     * region's FSMANAGEMENT group. Descendants are covered through the closure table by the callers.
     */
    Set<UUID> administratedScopes(UUID actorId) {
        Set<UUID> adminOf = admins.findRegionIds(actorId);
        if (adminOf.isEmpty()) {
            return Set.of();
        }
        Set<UUID> scopes = new HashSet<>();
        Map<UUID, Region> byId = regions.findByIdIn(adminOf).stream()
                .collect(Collectors.toMap(Region::getId, region -> region));
        byId.values().stream().filter(region -> !region.isGroup()).map(Region::getId).forEach(scopes::add);
        for (RegionFunction assignment : functions.findByGroupIdIn(adminOf)) {
            if (assignment.getFunction() == WorkingGroupFunction.FSMANAGEMENT) {
                scopes.add(assignment.getRegionId());
            }
        }
        return scopes;
    }
}
