package network.foodsharing.onboarding.web;

import java.util.UUID;
import network.foodsharing.api.PublicOnboardingApi;
import network.foodsharing.api.model.Explainer;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explainer API (implemented in T22).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class PublicOnboardingController implements PublicOnboardingApi {

    @Override
    public ResponseEntity<Explainer> getExplainer(UUID regionId) {
        throw ApiException.notImplemented("getExplainer");
    }
}
