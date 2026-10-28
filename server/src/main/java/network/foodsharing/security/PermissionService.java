package network.foodsharing.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * One place for permission decisions (plan §9, docs/conventions.md A10), three layers:
 * <ol>
 *   <li>the global role hierarchy FOODSHARER &lt; FOODSAVER &lt; STORE_MANAGER &lt; AMBASSADOR &lt; ORGA;</li>
 *   <li>scoped roles: region / working-group membership and admin, working-group functions;</li>
 *   <li>region-scoped feature {@link Permission}s, granted directly or through a working-group function.</li>
 * </ol>
 * ORGA passes every scoped check. Layers 2 and 3 come from the {@link ScopedPermissionResolver} bean of the region
 * module; without one (slice tests) nobody holds scoped roles.
 */
@Service
public class PermissionService {

    private static final List<Role> HIERARCHY = List.of(
            Role.FOODSHARER, Role.FOODSAVER, Role.STORE_MANAGER, Role.AMBASSADOR, Role.ORGA);

    private final ObjectProvider<ScopedPermissionResolver> resolvers;
    private final ScopedPermissionResolver fallback = new NoopScopedPermissionResolver();

    @Autowired
    public PermissionService(ObjectProvider<ScopedPermissionResolver> resolvers) {
        this.resolvers = resolvers;
    }

    /** Without scoped roles (unit tests). */
    public PermissionService() {
        this(null);
    }

    public static int rank(Role role) {
        return HIERARCHY.indexOf(role);
    }

    /** True when {@code actual} is {@code required} or a higher role. */
    public static boolean isAtLeast(Role actual, Role required) {
        return actual != null && required != null && rank(actual) >= rank(required);
    }

    /** ROLE_&lt;role&gt; for the role and every role below it. */
    public static List<GrantedAuthority> authoritiesFor(Role role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Role candidate : HIERARCHY) {
            if (isAtLeast(role, candidate)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + candidate.name()));
            }
        }
        return authorities;
    }

    // ------------------------------------------------------------------------------------------- layer 1: role

    public boolean hasRole(SessionPrincipal principal, Role required) {
        return principal != null && isAtLeast(principal.role(), required);
    }

    public boolean isOrga(SessionPrincipal principal) {
        return principal != null && principal.role() == Role.ORGA;
    }

    // ------------------------------------------------------------------------------- layer 2: scoped roles

    public boolean isMemberOf(SessionPrincipal principal, UUID regionOrGroupId) {
        return principal != null && regionOrGroupId != null && resolver().isMember(principal.userId(), regionOrGroupId);
    }

    /** Ambassador of the region / admin of the working group (ORGA is not automatically an ambassador). */
    public boolean isAmbassador(SessionPrincipal principal, UUID regionOrGroupId) {
        return principal != null && regionOrGroupId != null && resolver().isAdmin(principal.userId(), regionOrGroupId);
    }

    /** Ambassador of the region / admin of the group, or ORGA. */
    public boolean isAmbassadorOrOrga(SessionPrincipal principal, UUID regionOrGroupId) {
        return isOrga(principal) || isAmbassador(principal, regionOrGroupId);
    }

    public boolean isAmbassadorOfAny(SessionPrincipal principal) {
        return principal != null && resolver().isAdminOfAny(principal.userId());
    }

    /** Function rights in a region: ORGA; else the function group's admins; else the region's ambassadors. */
    public boolean hasFunction(SessionPrincipal principal, WorkingGroupFunction function, UUID regionId) {
        if (principal == null || regionId == null) {
            return false;
        }
        return isOrga(principal) || resolver().hasFunctionGroupPermission(principal.userId(), function, regionId);
    }

    /** ORGA, ambassador of the region or admin of its FSMANAGEMENT group. */
    public boolean mayAdministrateRegion(SessionPrincipal principal, UUID regionId) {
        if (principal == null || regionId == null) {
            return false;
        }
        return isOrga(principal) || resolver().mayAdministrateRegion(principal.userId(), regionId);
    }

    /** ORGA, or administrates (ambassador / FSMANAGEMENT admin) a region the user is a member of. */
    public boolean mayAdministrateUser(SessionPrincipal principal, UUID userId) {
        if (principal == null || userId == null) {
            return false;
        }
        return isOrga(principal) || resolver().mayAdministrateUser(principal.userId(), userId);
    }

    /** Batch form of {@link #mayAdministrateUser} for lists (ORGA: all). */
    public Set<UUID> administrableUsers(SessionPrincipal principal, java.util.Collection<UUID> userIds) {
        if (principal == null || userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        if (isOrga(principal)) {
            return new java.util.HashSet<>(userIds);
        }
        return resolver().administrableUsers(principal.userId(), userIds);
    }

    // ---------------------------------------------------------------------- layer 3: region permissions

    /** Region-scoped permission: ORGA everywhere; else a direct grant or a grant through a function. */
    public boolean has(SessionPrincipal principal, Permission permission, UUID regionId) {
        if (principal == null || permission == null) {
            return false;
        }
        if (isOrga(principal)) {
            return true;
        }
        return regionId != null && resolver().hasRegionPermission(principal.userId(), permission, regionId);
    }

    /** The permission in at least one region (ORGA: always). */
    public boolean hasAnywhere(SessionPrincipal principal, Permission permission) {
        if (principal == null) {
            return false;
        }
        return isOrga(principal) || resolver().permissionsByRegion(principal.userId()).values().stream()
                .anyMatch(permissions -> permissions.contains(permission));
    }

    /**
     * {@code SessionInfo.permissions}: {@code "<PERMISSION>@<regionId>"}, for ORGA {@code "<PERMISSION>@*"}; sorted.
     */
    public List<String> effectivePermissions(SessionPrincipal principal) {
        if (principal == null) {
            return List.of();
        }
        if (isOrga(principal)) {
            return java.util.Arrays.stream(Permission.values()).map(permission -> permission.name() + "@*").toList();
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<UUID, Set<Permission>> entry : resolver().permissionsByRegion(principal.userId()).entrySet()) {
            for (Permission permission : entry.getValue()) {
                result.add(permission.name() + "@" + entry.getKey());
            }
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------------------------- require variants

    /** @throws ApiException 401 without principal, 403 ACCESS_DENIED without the permission */
    public void require(SessionPrincipal principal, Permission permission, UUID regionId) {
        requireTrue(principal, has(principal, permission, regionId));
    }

    public void requireMember(SessionPrincipal principal, UUID regionOrGroupId) {
        requireTrue(principal, isOrga(principal) || isMemberOf(principal, regionOrGroupId));
    }

    public void requireAmbassadorOrOrga(SessionPrincipal principal, UUID regionOrGroupId) {
        requireTrue(principal, isAmbassadorOrOrga(principal, regionOrGroupId));
    }

    public void requireFunction(SessionPrincipal principal, WorkingGroupFunction function, UUID regionId) {
        requireTrue(principal, hasFunction(principal, function, regionId));
    }

    public void requireMayAdministrateRegion(SessionPrincipal principal, UUID regionId) {
        requireTrue(principal, mayAdministrateRegion(principal, regionId));
    }

    public void requireMayAdministrateUser(SessionPrincipal principal, UUID userId) {
        requireTrue(principal, mayAdministrateUser(principal, userId));
    }

    public void requireOrga(SessionPrincipal principal) {
        requireTrue(principal, isOrga(principal));
    }

    private static void requireTrue(SessionPrincipal principal, boolean allowed) {
        if (principal == null) {
            throw ApiException.unauthenticated();
        }
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private ScopedPermissionResolver resolver() {
        if (resolvers == null) {
            return fallback;
        }
        ScopedPermissionResolver resolver = resolvers.getIfAvailable();
        return resolver == null ? fallback : resolver;
    }
}
