package network.foodsharing.admin.web;

import java.util.List;
import network.foodsharing.api.AdminIntegrationsApi;
import network.foodsharing.api.model.Integration;
import network.foodsharing.api.model.IntegrationStatus;
import network.foodsharing.api.model.IntegrationTest;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration status, probes and tests (ORGA, M4 B3).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class AdminIntegrationsController implements AdminIntegrationsApi {

    @Override
    public ResponseEntity<List<IntegrationStatus>> listIntegrations() {
        throw ApiException.notImplemented("listIntegrations");
    }

    @Override
    public ResponseEntity<IntegrationStatus> probeIntegration(Integration integration) {
        throw ApiException.notImplemented("probeIntegration");
    }

    @Override
    public ResponseEntity<IntegrationStatus> testIntegration(Integration integration, IntegrationTest integrationTest) {
        throw ApiException.notImplemented("testIntegration");
    }
}
