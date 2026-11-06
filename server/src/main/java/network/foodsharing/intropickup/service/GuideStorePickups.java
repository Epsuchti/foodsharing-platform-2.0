package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.store.domain.Store;

/**
 * SPI implemented by the pickup module (EA16, decision 169): the guide's own store pickups, the source of EinAb
 * candidates and of the link check. intropickup must not depend on pickup (pickup already reads EinAbs for the day
 * agenda), so the pickup module provides this bean; {@link IntroPickupDefaults} has a no-op one until then.
 */
public interface GuideStorePickups {

    /** A future sign-up of the current user ({@code confirmed}: by a manager or the automation). */
    record OwnPickup(Store store, Instant pickupAt, boolean confirmed) {
    }

    /** Future sign-ups of the current user, soonest first; empty when the user may not know stores (S17). */
    List<OwnPickup> myUpcoming();

    /** Does {@code userId} hold a confirmed sign-up for the store's pickup at {@code pickupAt}? */
    boolean hasConfirmedSignup(UUID userId, UUID storeId, Instant pickupAt);
}
