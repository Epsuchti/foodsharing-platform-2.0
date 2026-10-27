package network.foodsharing.intropickup.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.IntroPickupsApi;
import network.foodsharing.api.model.BookableIntroPickupPage;
import network.foodsharing.api.model.IntroPickupCategory;
import network.foodsharing.api.model.IntroPickupSubscription;
import network.foodsharing.api.model.MyBooking;
import network.foodsharing.api.model.SubscriptionUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * EinAb user API (implemented in T15).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class IntroPickupsController implements IntroPickupsApi {

    @Override
    public ResponseEntity<MyBooking> bookSlot(UUID slotId) {
        throw ApiException.notImplemented("bookSlot");
    }

    @Override
    public ResponseEntity<Void> cancelMyBooking(UUID slotId) {
        throw ApiException.notImplemented("cancelMyBooking");
    }

    @Override
    public ResponseEntity<String> getIntroPickupIcs(UUID introPickupId) {
        throw ApiException.notImplemented("getIntroPickupIcs");
    }

    @Override
    public ResponseEntity<IntroPickupSubscription> getMySubscription() {
        throw ApiException.notImplemented("getMySubscription");
    }

    @Override
    public ResponseEntity<BookableIntroPickupPage> listBookableIntroPickups(UUID regionId, String search, IntroPickupCategory category, Boolean visitFairteiler, Integer page, Integer size) {
        throw ApiException.notImplemented("listBookableIntroPickups");
    }

    @Override
    public ResponseEntity<List<MyBooking>> listMyBookings(Boolean past) {
        throw ApiException.notImplemented("listMyBookings");
    }

    @Override
    public ResponseEntity<IntroPickupSubscription> subscribe(SubscriptionUpdate subscriptionUpdate) {
        throw ApiException.notImplemented("subscribe");
    }

    @Override
    public ResponseEntity<Void> unsubscribeMine() {
        throw ApiException.notImplemented("unsubscribeMine");
    }
}
