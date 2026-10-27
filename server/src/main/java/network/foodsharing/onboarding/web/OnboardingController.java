package network.foodsharing.onboarding.web;

import java.util.UUID;
import network.foodsharing.api.OnboardingApi;
import network.foodsharing.api.model.Journey;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onboarding journey API (implemented in T22).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class OnboardingController implements OnboardingApi {

    @Override
    public ResponseEntity<Void> confirmRegionStep(UUID regionId, UUID userId, String stepKey) {
        throw ApiException.notImplemented("confirmRegionStep");
    }

    @Override
    public ResponseEntity<Journey> getMyJourney() {
        throw ApiException.notImplemented("getMyJourney");
    }

    @Override
    public ResponseEntity<Journey> markChapterRead(String key) {
        throw ApiException.notImplemented("markChapterRead");
    }
}
