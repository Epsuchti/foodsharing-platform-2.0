package network.foodsharing.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Spring Security authentication carrying a {@link SessionPrincipal}; authorities = ROLE_* incl. implied roles. */
public class SessionAuthenticationToken extends AbstractAuthenticationToken {

    private final SessionPrincipal principal;

    public SessionAuthenticationToken(SessionPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    public static SessionAuthenticationToken of(SessionPrincipal principal) {
        return new SessionAuthenticationToken(principal, PermissionService.authoritiesFor(principal.role()));
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public SessionPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.userId().toString();
    }
}
