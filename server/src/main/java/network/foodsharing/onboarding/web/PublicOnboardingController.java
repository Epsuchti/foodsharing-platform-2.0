package network.foodsharing.onboarding.web;

import java.util.UUID;
import network.foodsharing.api.PublicOnboardingApi;
import network.foodsharing.api.model.Explainer;
import network.foodsharing.onboarding.mapper.OnboardingMapper;
import network.foodsharing.onboarding.service.ExplainerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public explainer aggregate (plan §11.2): counters, journey preview, optional region card. */
@RestController
public class PublicOnboardingController implements PublicOnboardingApi {

    private final ExplainerService explainer;
    private final OnboardingMapper mapper;

    public PublicOnboardingController(ExplainerService explainer, OnboardingMapper mapper) {
        this.explainer = explainer;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Explainer> getExplainer(UUID regionId) {
        return ResponseEntity.ok(mapper.toExplainer(explainer.explainer(regionId)));
    }
}
