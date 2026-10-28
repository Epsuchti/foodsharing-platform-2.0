package network.foodsharing.user.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The partner apps a user connected through the OAuth provider (C13, {@code /account/connections}); implemented by
 * {@code oauthserver} (which depends on {@code user}, not vice versa).
 */
public interface ConnectionsPort {

    record Connection(String clientId, String clientName, Set<String> scopes, Instant grantedAt) {
    }

    List<Connection> list(UUID userId);

    /** Revokes the remembered consent and every refresh token of the user for the client. @throws 404 if unknown */
    void revoke(UUID userId, String clientId);
}
