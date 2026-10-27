package network.foodsharing.verification.service;

import java.util.UUID;

/**
 * SPI: other modules may block un-verifying a user (M3 registers "no future pickups" → 409 HAS_FUTURE_PICKUPS).
 */
public interface UnverifyGuard {

    /** @throws network.foodsharing.common.error.ApiException when the user must stay verified */
    void check(UUID userId);
}
