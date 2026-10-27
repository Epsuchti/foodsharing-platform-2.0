package network.foodsharing.intropickup.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.IntroPickupsApi;
import network.foodsharing.api.model.BookableIntroPickupPage;
import network.foodsharing.api.model.IntroPickupCategory;
import network.foodsharing.api.model.IntroPickupSubscription;
import network.foodsharing.api.model.MyBooking;
import network.foodsharing.api.model.SubscriptionUpdate;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.intropickup.mapper.IntroPickupMapper;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.intropickup.service.IntroPickupIcalService;
import network.foodsharing.intropickup.service.IntroPickupSubscriptionService;
import network.foodsharing.region.service.RegionService;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** EinAb user API: bookable list, booking, own bookings, calendar file, subscription (plan §14.1 EA5–EA9). */
@RestController
public class IntroPickupsController implements IntroPickupsApi {

    static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final IntroPickupBookingService booking;
    private final IntroPickupIcalService ical;
    private final IntroPickupSubscriptionService subscriptions;
    private final RegionService regions;
    private final IntroPickupMapper mapper;

    public IntroPickupsController(IntroPickupBookingService booking, IntroPickupIcalService ical,
                                  IntroPickupSubscriptionService subscriptions, RegionService regions,
                                  IntroPickupMapper mapper) {
        this.booking = booking;
        this.ical = ical;
        this.subscriptions = subscriptions;
        this.regions = regions;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<MyBooking> bookSlot(UUID slotId) {
        return ResponseEntity.ok(mapper.toMyBooking(booking.book(slotId)));
    }

    @Override
    public ResponseEntity<Void> cancelMyBooking(UUID slotId) {
        booking.cancelMine(slotId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<String> getIntroPickupIcs(UUID introPickupId) {
        return ResponseEntity.ok().contentType(CALENDAR).body(ical.pickupIcs(introPickupId));
    }

    @Override
    public ResponseEntity<IntroPickupSubscription> getMySubscription() {
        var subscription = subscriptions.mine();
        return ResponseEntity.ok(mapper.toSubscription(subscription,
                regions.find(subscription.getRegionId()).orElse(null)));
    }

    @Override
    public ResponseEntity<BookableIntroPickupPage> listBookableIntroPickups(UUID regionId, String search,
                                                                           IntroPickupCategory category,
                                                                           Boolean visitFairteiler, Integer page,
                                                                           Integer size) {
        Page<IntroPickupBookingService.BookableView> result = booking.bookable(regionId, search,
                IntroPickupMapper.category(category), visitFairteiler, page, size);
        BookableIntroPickupPage model = new BookableIntroPickupPage();
        model.setItems(result.getContent().stream().map(mapper::toBookable).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<List<MyBooking>> listMyBookings(Boolean past) {
        return ResponseEntity.ok(booking.myBookings(Boolean.TRUE.equals(past)).stream().map(mapper::toMyBooking)
                .toList());
    }

    @Override
    public ResponseEntity<IntroPickupSubscription> subscribe(SubscriptionUpdate update) {
        var subscription = subscriptions.subscribe(update.getRegionId(), IntroPickupMapper.channel(update.getChannel()),
                update.getDeliveryEmail(), update.getLanguage());
        return ResponseEntity.ok(mapper.toSubscription(subscription,
                regions.find(subscription.getRegionId()).orElse(null)));
    }

    @Override
    public ResponseEntity<Void> unsubscribeMine() {
        subscriptions.unsubscribeMine();
        return ResponseEntity.noContent().build();
    }
}
