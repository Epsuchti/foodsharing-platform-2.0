package network.foodsharing.admin.web;

import java.util.LinkedHashMap;
import java.util.Map;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.PublicSettingsApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Feature toggle states for the client bootstrap (A43, anonymous). */
@RestController
public class PublicSettingsController implements PublicSettingsApi {

    private final FeatureToggleService toggles;

    public PublicSettingsController(FeatureToggleService toggles) {
        this.toggles = toggles;
    }

    @Override
    public ResponseEntity<Map<String, Boolean>> getPublicFeatureToggles() {
        Map<String, Boolean> body = new LinkedHashMap<>();
        toggles.states().forEach((toggle, enabled) -> body.put(toggle.name(), enabled));
        return ResponseEntity.ok(body);
    }
}
