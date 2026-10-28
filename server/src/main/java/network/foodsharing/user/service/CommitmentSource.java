package network.foodsharing.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Commitments of a user for the profile's weekly overview (A6; pickup implements it in B2, basket in B5, event in
 * B7). Pattern A22: the user module owns the SPI, feature modules implement it.
 */
public interface CommitmentSource {

    enum Kind {
        PICKUP,
        BASKET_OFFERED,
        BASKET_SHARED,
        EVENT_CREATED,
        EVENT_PARTICIPATED
    }

    record Commitment(Kind kind, Instant at) {
    }

    /** Commitments with {@code from <= at < to}. */
    List<Commitment> commitments(UUID userId, Instant from, Instant to);
}
