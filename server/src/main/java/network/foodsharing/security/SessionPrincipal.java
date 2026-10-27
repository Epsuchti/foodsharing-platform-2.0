package network.foodsharing.security;

import java.util.UUID;
import network.foodsharing.api.model.Role;

/** The authenticated user of a request, resolved from the {@code fs_session} cookie. */
public record SessionPrincipal(UUID userId, UUID sessionId, Role role, String email) {
}
