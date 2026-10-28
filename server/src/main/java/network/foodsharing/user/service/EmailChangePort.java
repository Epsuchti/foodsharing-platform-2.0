package network.foodsharing.user.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.user.domain.User;

/**
 * Email change (A5, upstream {@code fs_mailchange}); implemented by the {@code auth} module, which owns login
 * identities, sessions, the email blocklist and the mail outbox access.
 */
public interface EmailChangePort {

    /** A pending (not expired) change. */
    record Pending(String newEmail, Instant requestedAt) {
    }

    /** The current user's pending change. */
    Optional<Pending> pending();

    /** The current user (self) or {@code mayAdministrateUser} (others) requests a new address for {@code userId}. */
    void request(UUID userId, String newEmail);

    /** The current user confirms with the token mailed to the new address. */
    User confirm(String token);

    /** The current user aborts their pending change. */
    void abort();

    /** Anyone with the abort token mailed to the old address aborts the change. */
    void abortByToken(String token);
}
