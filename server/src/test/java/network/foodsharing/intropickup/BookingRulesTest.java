package network.foodsharing.intropickup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.service.BookingRules;
import network.foodsharing.intropickup.service.BookingRules.Booker;
import network.foodsharing.intropickup.service.BookingRules.Booking;
import network.foodsharing.region.service.RegionSettingsService.IntroPickupSettings;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The booking rule chain (reference PublicService.bookSlot): each rule, in order, and the ignored-category bypass. */
class BookingRulesTest {

    private static final UUID GUIDE_A = UUID.randomUUID();
    private static final UUID GUIDE_B = UUID.randomUUID();
    private static final UUID GUIDE_C = UUID.randomUUID();
    private static final Booker OK = new Booker(true, true, false);
    private static final IntroPickupSettings STRICT = new IntroPickupSettings(3, true, true, true, Set.of("ONLINE"),
            null);
    private static final IntroPickupSettings LOOSE = new IntroPickupSettings(3, false, false, false, Set.of(), null);

    private static IntroPickup pickup(IntroPickupCategory category, UUID guide, boolean visit, Integer minimum) {
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.randomUUID());
        pickup.setCategory(category);
        pickup.setGuideId(guide);
        pickup.setVisitFairteiler(visit);
        pickup.setMinimumPickupCount(minimum);
        return pickup;
    }

    private static Booking booking(IntroPickupCategory category, UUID guide, boolean visit, SlotStatus status) {
        return new Booking(UUID.randomUUID(), category, guide, visit, status);
    }

    private static Optional<ApiErrorCode> check(Booker booker, boolean available, IntroPickup pickup,
                                                List<Booking> bookings, IntroPickupSettings settings) {
        return BookingRules.check(booker, available, pickup, bookings, settings);
    }

    @Test
    void userRulesComeFirst() {
        IntroPickup pickup = pickup(IntroPickupCategory.BAKERY, GUIDE_A, false, null);
        assertThat(check(new Booker(false, false, true), false, pickup, List.of(), STRICT))
                .contains(ApiErrorCode.BOOKING_USER_DISABLED);
        assertThat(check(new Booker(true, false, true), false, pickup, List.of(), STRICT))
                .contains(ApiErrorCode.NOT_REGION_MEMBER);
        assertThat(check(new Booker(true, true, true), false, pickup, List.of(), STRICT))
                .contains(ApiErrorCode.GUIDES_CANNOT_BOOK);
        assertThat(check(OK, false, pickup, List.of(), STRICT)).contains(ApiErrorCode.SLOT_NOT_AVAILABLE);
        assertThat(check(OK, true, pickup, List.of(), STRICT)).isEmpty();
    }

    @Test
    void alreadyBookedInTheSameEinAb() {
        IntroPickup pickup = pickup(IntroPickupCategory.ONLINE, GUIDE_A, false, null);
        Booking same = new Booking(pickup.getId(), IntroPickupCategory.ONLINE, GUIDE_A, false, SlotStatus.BOOKED);
        assertThat(check(OK, true, pickup, List.of(same), STRICT))
                .contains(ApiErrorCode.USER_ALREADY_BOOKED_IN_INTRO_PICKUP);
        Booking cancelled = new Booking(pickup.getId(), IntroPickupCategory.ONLINE, GUIDE_A, false,
                SlotStatus.CANCELLED);
        assertThat(check(OK, true, pickup, List.of(cancelled), STRICT)).isEmpty();
    }

    @Test
    void duplicateGuideThenCategoryThenLimit() {
        IntroPickup bakeryA = pickup(IntroPickupCategory.BAKERY, GUIDE_A, false, null);
        List<Booking> withA = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, false, SlotStatus.DONE));
        assertThat(check(OK, true, bakeryA, withA, STRICT)).contains(ApiErrorCode.USER_ALREADY_BOOKED_WITH_GUIDE);
        IntroPickup bakeryB = pickup(IntroPickupCategory.BAKERY, GUIDE_B, false, null);
        assertThat(check(OK, true, bakeryB, withA, STRICT)).contains(ApiErrorCode.USER_ALREADY_BOOKED_IN_CATEGORY);
        assertThat(check(OK, true, bakeryB, withA, LOOSE)).isEmpty();
        List<Booking> three = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, true, SlotStatus.DONE),
                booking(IntroPickupCategory.MARKET, GUIDE_B, false, SlotStatus.BOOKED),
                booking(IntroPickupCategory.TAKEOUT, GUIDE_C, false, SlotStatus.DONE));
        IntroPickup restaurant = pickup(IntroPickupCategory.RESTAURANT, UUID.randomUUID(), false, null);
        assertThat(check(OK, true, restaurant, three, STRICT)).contains(ApiErrorCode.USER_BOOKING_LIMIT_REACHED);
    }

    @Test
    void fairteilerVisitIsRequiredByTheThird() {
        List<Booking> two = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, false, SlotStatus.DONE),
                booking(IntroPickupCategory.MARKET, GUIDE_B, false, SlotStatus.BOOKED));
        IntroPickup noVisit = pickup(IntroPickupCategory.RESTAURANT, GUIDE_C, false, null);
        assertThat(check(OK, true, noVisit, two, STRICT))
                .contains(ApiErrorCode.FAIRTEILER_VISIT_REQUIRED_FOR_THIRD_INTRO_PICKUP);
        IntroPickup visit = pickup(IntroPickupCategory.RESTAURANT, GUIDE_C, true, null);
        assertThat(check(OK, true, visit, two, STRICT)).isEmpty();
        // a visit in an ignored category satisfies the rule
        List<Booking> withOnlineVisit = new ArrayList<>(two);
        withOnlineVisit.add(booking(IntroPickupCategory.ONLINE, GUIDE_C, true, SlotStatus.DONE));
        assertThat(check(OK, true, noVisit, withOnlineVisit, STRICT)).isEmpty();
        assertThat(check(OK, true, noVisit, two, LOOSE)).isEmpty();
    }

    @Test
    void minimumPickupCountCountsDoneOnly() {
        IntroPickup minimumTwo = pickup(IntroPickupCategory.MESSE, GUIDE_C, false, 2);
        List<Booking> oneDoneOneBooked = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, false, SlotStatus.DONE),
                booking(IntroPickupCategory.MARKET, GUIDE_B, false, SlotStatus.BOOKED));
        assertThat(check(OK, true, minimumTwo, oneDoneOneBooked, LOOSE))
                .contains(ApiErrorCode.MINIMUM_PICKUP_COUNT_NOT_REACHED);
        List<Booking> twoDone = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, false, SlotStatus.DONE),
                booking(IntroPickupCategory.MARKET, GUIDE_B, false, SlotStatus.DONE));
        assertThat(check(OK, true, minimumTwo, twoDone, LOOSE)).isEmpty();
    }

    @Test
    void ignoredCategoriesBypassTheCategoryRulesAndDoNotCount() {
        List<Booking> three = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, true, SlotStatus.DONE),
                booking(IntroPickupCategory.MARKET, GUIDE_B, false, SlotStatus.DONE),
                booking(IntroPickupCategory.TAKEOUT, GUIDE_C, false, SlotStatus.DONE));
        IntroPickup online = pickup(IntroPickupCategory.ONLINE, GUIDE_A, false, 99);
        assertThat(check(OK, true, online, three, STRICT)).isEmpty();
        List<Booking> twoAndOnline = List.of(booking(IntroPickupCategory.BAKERY, GUIDE_A, true, SlotStatus.DONE),
                booking(IntroPickupCategory.MARKET, GUIDE_B, false, SlotStatus.DONE),
                booking(IntroPickupCategory.ONLINE, GUIDE_C, false, SlotStatus.DONE));
        IntroPickup restaurant = pickup(IntroPickupCategory.RESTAURANT, GUIDE_C, false, null);
        assertThat(check(OK, true, restaurant, twoAndOnline, STRICT)).isEmpty();
    }

    @Test
    void exceptionStatus() {
        assertThat(BookingRules.toException(ApiErrorCode.GUIDES_CANNOT_BOOK).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ApiException conflict = BookingRules.toException(ApiErrorCode.USER_BOOKING_LIMIT_REACHED);
        assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
