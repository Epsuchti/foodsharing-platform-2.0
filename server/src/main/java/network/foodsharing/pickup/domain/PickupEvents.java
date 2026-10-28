package network.foodsharing.pickup.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of the pickup module (A24), published inside the mutating transaction; consumers (automation) listen
 * with {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code REQUIRES_NEW}. Pickup never depends on consumers.
 */
public final class PickupEvents {

    private PickupEvents() {
    }

    /** A user signed up for a pickup (confirmed = pre-confirmed manager sign-up). */
    public record PickupSignedUpEvent(UUID storeId, Instant pickupAt, UUID userId, boolean confirmed,
                                      Instant signedUpAt) {
    }

    /** A sign-up ended: by the user themself ({@code byUserId == userId}), a manager, or the automation (null). */
    public record PickupSignedOutEvent(UUID storeId, Instant pickupAt, UUID userId, UUID byUserId, Instant at) {

        public boolean self() {
            return userId.equals(byUserId);
        }
    }

    /** A sign-up was confirmed by a manager ({@code byUserId}) or the automation (null). */
    public record PickupConfirmedEvent(UUID storeId, Instant pickupAt, UUID userId, UUID byUserId) {
    }
}
