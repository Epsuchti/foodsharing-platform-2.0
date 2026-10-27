package network.foodsharing.security;

import java.util.Optional;

/**
 * Resolves a raw session token (cookie value) to a principal. Implemented by the auth module
 * ({@code auth.service.AuthSessionService}), which also slides the expiry.
 */
public interface SessionAuthenticator {

    Optional<SessionPrincipal> authenticate(String rawToken);
}
