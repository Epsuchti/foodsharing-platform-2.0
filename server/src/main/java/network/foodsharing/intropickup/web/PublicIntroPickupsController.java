package network.foodsharing.intropickup.web;

import network.foodsharing.api.PublicIntroPickupsApi;
import network.foodsharing.api.model.IntroPickupCategory;
import network.foodsharing.api.model.MessageResponse;
import network.foodsharing.api.model.PublicIntroPickupPage;
import network.foodsharing.api.model.UnsubscribeRequest;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public EinAb API (implemented in T15).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class PublicIntroPickupsController implements PublicIntroPickupsApi {

    @Override
    public ResponseEntity<String> getGuideIcalFeed(String token) {
        throw ApiException.notImplemented("getGuideIcalFeed");
    }

    @Override
    public ResponseEntity<PublicIntroPickupPage> listPublicIntroPickups(String slug, IntroPickupCategory category, Boolean visitFairteiler, Integer page, Integer size) {
        throw ApiException.notImplemented("listPublicIntroPickups");
    }

    @Override
    public ResponseEntity<MessageResponse> unsubscribeByToken(UnsubscribeRequest unsubscribeRequest) {
        throw ApiException.notImplemented("unsubscribeByToken");
    }
}
