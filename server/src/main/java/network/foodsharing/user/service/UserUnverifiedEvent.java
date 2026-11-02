package network.foodsharing.user.service;

import java.util.UUID;

/**
 * Published in the un-verifying transaction when a user loses verification; listeners run in that transaction (the
 * store module ends the user's store memberships, S17 D7).
 */
public record UserUnverifiedEvent(UUID userId) {
}
