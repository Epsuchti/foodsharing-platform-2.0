package network.foodsharing.security;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * One place for permission decisions (plan §9). M1: the global role hierarchy
 * FOODSHARER &lt; FOODSAVER &lt; STORE_MANAGER &lt; AMBASSADOR &lt; ORGA. Scoped roles and region permissions are
 * added in milestone 2 behind {@link #has(SessionPrincipal, Permission, UUID)}.
 */
@Service
public class PermissionService {

    private static final List<Role> HIERARCHY = List.of(
            Role.FOODSHARER, Role.FOODSAVER, Role.STORE_MANAGER, Role.AMBASSADOR, Role.ORGA);

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

    public boolean hasRole(SessionPrincipal principal, Role required) {
        return principal != null && isAtLeast(principal.role(), required);
    }

    public boolean isOrga(SessionPrincipal principal) {
        return principal != null && principal.role() == Role.ORGA;
    }

    /**
     * Region-scoped permission check. M1: only ORGA holds permissions (everywhere); milestone 2 resolves region
     * permissions and working-group functions here.
     */
    public boolean has(SessionPrincipal principal, Permission permission, UUID scopeId) {
        return isOrga(principal);
    }

    /** Names for {@code SessionInfo.permissions}. M1: empty (region permissions arrive in milestone 2). */
    public List<String> effectivePermissions(SessionPrincipal principal) {
        return List.of();
    }
}
