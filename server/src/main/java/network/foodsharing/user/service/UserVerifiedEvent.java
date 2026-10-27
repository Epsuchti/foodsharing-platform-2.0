package network.foodsharing.user.service;

import java.util.UUID;

/**
 * Published (in the verifying transaction) when a user becomes verified; other modules react after commit (e.g. the
 * EinAb module deactivates the user's notification subscription).
 */
public record UserVerifiedEvent(UUID userId, UUID verifiedBy) {
}
