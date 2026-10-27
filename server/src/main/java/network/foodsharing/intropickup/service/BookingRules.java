package network.foodsharing.intropickup.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.region.service.RegionSettingsService.IntroPickupSettings;

/**
 * The booking rule chain of the reference {@code PublicService.bookSlot}, in this order: user active (403
 * BOOKING_USER_DISABLED); member of the region (409 NOT_REGION_MEMBER); not a guide of the region (403
 * GUIDES_CANNOT_BOOK); slot available (409 SLOT_NOT_AVAILABLE); not already booked in this EinAb; then, unless the
 * category is ignored by the region: duplicate guide, duplicate category, booking limit, Fairteiler visit by the
 * third EinAb, minimum pickup count. "Active" bookings are BOOKED or DONE; ignored categories never count, except that
 * a Fairteiler visit in an ignored category still satisfies the Fairteiler rule (as in the reference).
 */
public final class BookingRules {

    static final Set<SlotStatus> ACTIVE = Set.of(SlotStatus.BOOKED, SlotStatus.DONE);

    /** An existing booking of the user in the region. */
    public record Booking(UUID introPickupId, IntroPickupCategory category, UUID guideId, boolean visitFairteiler,
                          SlotStatus status) {
    }

    /** What is known about the booking user. */
    public record Booker(boolean active, boolean member, boolean guide) {
    }

    private BookingRules() {
    }

    /** @return the first failing rule, or empty when the user may book a free slot of the EinAb */
    public static Optional<ApiErrorCode> check(Booker booker, boolean slotAvailable, IntroPickup pickup,
                                               List<Booking> bookings, IntroPickupSettings settings) {
        if (!booker.active()) {
            return Optional.of(ApiErrorCode.BOOKING_USER_DISABLED);
        }
        if (!booker.member()) {
            return Optional.of(ApiErrorCode.NOT_REGION_MEMBER);
        }
        if (booker.guide()) {
            return Optional.of(ApiErrorCode.GUIDES_CANNOT_BOOK);
        }
        if (!slotAvailable) {
            return Optional.of(ApiErrorCode.SLOT_NOT_AVAILABLE);
        }
        List<Booking> active = bookings.stream().filter(b -> ACTIVE.contains(b.status())).toList();
        if (active.stream().anyMatch(b -> b.introPickupId().equals(pickup.getId()))) {
            return Optional.of(ApiErrorCode.USER_ALREADY_BOOKED_IN_INTRO_PICKUP);
        }
        Set<String> ignored = settings.ignoredCategories() == null ? Set.of() : settings.ignoredCategories();
        if (ignored.contains(pickup.getCategory().name())) {
            return Optional.empty();
        }
        List<Booking> counting = active.stream().filter(b -> !ignored.contains(b.category().name())).toList();
        if (settings.preventDuplicateGuideBookings()
                && counting.stream().anyMatch(b -> b.guideId().equals(pickup.getGuideId()))) {
            return Optional.of(ApiErrorCode.USER_ALREADY_BOOKED_WITH_GUIDE);
        }
        if (settings.preventDuplicateCategoryBookings()
                && counting.stream().anyMatch(b -> b.category() == pickup.getCategory())) {
            return Optional.of(ApiErrorCode.USER_ALREADY_BOOKED_IN_CATEGORY);
        }
        if (counting.size() >= settings.maxActiveBookingsPerUser()) {
            return Optional.of(ApiErrorCode.USER_BOOKING_LIMIT_REACHED);
        }
        if (settings.requireFairteilerVisitByThirdIntroPickup() && !pickup.isVisitFairteiler()
                && counting.size() >= settings.maxActiveBookingsPerUser() - 1
                && active.stream().noneMatch(Booking::visitFairteiler)) {
            return Optional.of(ApiErrorCode.FAIRTEILER_VISIT_REQUIRED_FOR_THIRD_INTRO_PICKUP);
        }
        Integer minimum = pickup.getMinimumPickupCount();
        if (minimum != null && counting.stream().filter(b -> b.status() == SlotStatus.DONE).count() < minimum) {
            return Optional.of(ApiErrorCode.MINIMUM_PICKUP_COUNT_NOT_REACHED);
        }
        return Optional.empty();
    }

    /** The API error for a failed rule (403 for a disabled user or a guide, else 409). */
    public static ApiException toException(ApiErrorCode code) {
        return code == ApiErrorCode.BOOKING_USER_DISABLED || code == ApiErrorCode.GUIDES_CANNOT_BOOK
                ? ApiException.forbidden(code) : ApiException.conflict(code);
    }
}
