package network.foodsharing.region.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Next bookable introductory pickups of a region for the public region page (plan §13.2 R4). Implemented by the
 * intropickup module (which depends on the region module, not the other way round); without it the list is empty.
 */
public interface UpcomingIntroPickups {

    /** Public data only (no guide, no private address); {@code category} is the IntroPickupCategory name. */
    record PublicIntroPickupView(UUID id, String category, Instant startAt, String publicGeneralLocation,
                                 String businessName, String publicInfo, boolean visitFairteiler, int freeSlots,
                                 String regionSlug) {
    }

    List<PublicIntroPickupView> next(UUID regionId, int limit);
}
