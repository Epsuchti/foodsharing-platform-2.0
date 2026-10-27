package network.foodsharing.intropickup.web;

import network.foodsharing.api.PublicIntroPickupsApi;
import network.foodsharing.api.model.IntroPickupCategory;
import network.foodsharing.api.model.MessageResponse;
import network.foodsharing.api.model.PublicIntroPickupPage;
import network.foodsharing.api.model.UnsubscribeRequest;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.intropickup.mapper.IntroPickupMapper;
import network.foodsharing.intropickup.service.IntroPickupIcalService;
import network.foodsharing.intropickup.service.IntroPickupSubscriptionService;
import network.foodsharing.intropickup.service.PublicIntroPickupService;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.UpcomingIntroPickups;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public EinAb API: the public list of a region, the guide's iCal feed (token) and unsubscribe by token. */
@RestController
public class PublicIntroPickupsController implements PublicIntroPickupsApi {

    private final PublicIntroPickupService publicPickups;
    private final IntroPickupIcalService ical;
    private final IntroPickupSubscriptionService subscriptions;
    private final RegionMapper regionMapper;

    public PublicIntroPickupsController(PublicIntroPickupService publicPickups, IntroPickupIcalService ical,
                                        IntroPickupSubscriptionService subscriptions, RegionMapper regionMapper) {
        this.publicPickups = publicPickups;
        this.ical = ical;
        this.subscriptions = subscriptions;
        this.regionMapper = regionMapper;
    }

    @Override
    public ResponseEntity<String> getGuideIcalFeed(String token) {
        return ResponseEntity.ok().contentType(IntroPickupsController.CALENDAR).body(ical.guideFeed(token));
    }

    @Override
    public ResponseEntity<PublicIntroPickupPage> listPublicIntroPickups(String slug, IntroPickupCategory category,
                                                                       Boolean visitFairteiler, Integer page,
                                                                       Integer size) {
        Page<UpcomingIntroPickups.PublicIntroPickupView> result = publicPickups.list(slug,
                IntroPickupMapper.category(category), visitFairteiler, page, size);
        PublicIntroPickupPage model = new PublicIntroPickupPage();
        model.setItems(result.getContent().stream().map(regionMapper::toPublicIntroPickup).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<MessageResponse> unsubscribeByToken(UnsubscribeRequest request) {
        subscriptions.unsubscribeByToken(request.getToken());
        MessageResponse response = new MessageResponse();
        response.setMessage("UNSUBSCRIBED");
        return ResponseEntity.ok(response);
    }
}
