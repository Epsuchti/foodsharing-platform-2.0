package network.foodsharing.onboarding.web;

import java.util.UUID;
import network.foodsharing.api.OnboardingApi;
import network.foodsharing.api.model.Journey;
import network.foodsharing.onboarding.mapper.OnboardingMapper;
import network.foodsharing.onboarding.service.JourneyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Onboarding journey API (plan §11.1). */
@RestController
public class OnboardingController implements OnboardingApi {

    private final JourneyService journeys;
    private final OnboardingMapper mapper;

    public OnboardingController(JourneyService journeys, OnboardingMapper mapper) {
        this.journeys = journeys;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Void> confirmRegionStep(UUID regionId, UUID userId, String stepKey) {
        journeys.confirmRegionStep(regionId, userId, stepKey);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Journey> getMyJourney() {
        return ResponseEntity.ok(mapper.toJourney(journeys.mine()));
    }

    @Override
    public ResponseEntity<Journey> markChapterRead(String key) {
        return ResponseEntity.ok(mapper.toJourney(journeys.markChapterRead(key)));
    }
}
