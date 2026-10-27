package network.foodsharing.auth.service;

import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.security.SessionPrincipal;

/** Result of {@link AuthSessionService#create}: the cookie is already written to the response. */
public record CreatedSession(AuthSession session, SessionPrincipal principal) {
}
