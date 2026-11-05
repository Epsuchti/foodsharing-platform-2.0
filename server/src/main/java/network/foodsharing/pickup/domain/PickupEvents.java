package network.foodsharing.pickup.domain;

import java.time.Instant;
import java.util.List;
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

    /** Why several sign-ups of one user were deleted at once (EA16). */
    public enum SignupsDeletedReason { ALL_FUTURE_DELETED, LEFT_TEAM }

    /** One deleted sign-up: the pickup of a store. */
    public record DeletedSignup(UUID storeId, Instant pickupAt) {
    }

    /**
     * Several future sign-ups of {@code userId} were deleted at once ("delete all future pickups", leaving or removal
     * from the store team); {@code byUserId} is null without a signed-in actor (EA16, decision 169).
     */
    public record PickupSignupsDeletedEvent(UUID userId, List<DeletedSignup> signups, UUID byUserId,
                                            SignupsDeletedReason reason) {

        public PickupSignupsDeletedEvent {
            signups = List.copyOf(signups);
        }
    }

    /** A sign-up was confirmed by a manager ({@code byUserId}) or the automation (null). */
    public record PickupConfirmedEvent(UUID storeId, Instant pickupAt, UUID userId, UUID byUserId) {
    }
}
