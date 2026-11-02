package network.foodsharing.region.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Next bookable introductory pickups of a region for the public region page (plan §13.2 R4). Implemented by the
 * intropickup module (which depends on the region module, not the other way round); without it the list is empty.
 */
public interface UpcomingIntroPickups {

    /** Public data only (S17 D5): no guide, category, business name or location text; {@code city} null when online. */
    record PublicIntroPickupView(UUID id, Instant startAt, boolean online, String city, int freeSlots,
                                 String regionSlug) {
    }

    List<PublicIntroPickupView> next(UUID regionId, int limit);
}
