package network.foodsharing.admin.web;

import java.util.Map;
import network.foodsharing.api.PublicSettingsApi;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class PublicSettingsController implements PublicSettingsApi {

    @Override
    public ResponseEntity<Map<String, Boolean>> getPublicFeatureToggles() {
        throw ApiException.notImplemented("getPublicFeatureToggles");
    }
}
