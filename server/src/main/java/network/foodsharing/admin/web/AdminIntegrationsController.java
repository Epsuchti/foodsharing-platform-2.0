package network.foodsharing.admin.web;

import java.util.List;
import network.foodsharing.admin.service.AdminOperationsService;
import network.foodsharing.api.AdminIntegrationsApi;
import network.foodsharing.api.model.CircuitState;
import network.foodsharing.api.model.Integration;
import network.foodsharing.api.model.IntegrationMode;
import network.foodsharing.api.model.IntegrationStatus;
import network.foodsharing.api.model.IntegrationTest;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.integration.CircuitBreaker;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.security.CurrentActorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Integration breakers: status and probe (ORGA, A26). Test messages follow with the Telegram/SMTP work (B3). */
@RestController
public class AdminIntegrationsController implements AdminIntegrationsApi {

    private final AdminOperationsService operations;
    private final CurrentActorService currentActor;

    public AdminIntegrationsController(AdminOperationsService operations, CurrentActorService currentActor) {
        this.operations = operations;
        this.currentActor = currentActor;
    }

    @Override
    public ResponseEntity<List<IntegrationStatus>> listIntegrations() {
        currentActor.requireRole(Role.ORGA);
        return ResponseEntity.ok(operations.integrations().stream().map(this::toApi).toList());
    }

    @Override
    public ResponseEntity<IntegrationStatus> probeIntegration(Integration integration) {
        currentActor.requireRole(Role.ORGA);
        return ResponseEntity.ok(toApi(operations.probe(
                network.foodsharing.common.integration.Integration.valueOf(integration.name()))));
    }

    @Override
    public ResponseEntity<IntegrationStatus> testIntegration(Integration integration, IntegrationTest integrationTest) {
        currentActor.requireRole(Role.ORGA);
        return ResponseEntity.ok(toApi(operations.sendTest(
                network.foodsharing.common.integration.Integration.valueOf(integration.name()),
                currentActor.requireUser(), integrationTest == null ? null : integrationTest.getTarget())));
    }

    private IntegrationStatus toApi(CircuitBreaker.Snapshot s) {
        return new IntegrationStatus().integration(Integration.valueOf(s.integration().name()))
                .mode(IntegrationMode.valueOf(operations.mode(s.integration()).name()))
                .state(CircuitState.valueOf(s.state().name()))
                .lastSuccessAt(s.lastSuccessAt() == null ? null : TimeMapper.toOffset(s.lastSuccessAt()))
                .lastFailureAt(s.lastFailureAt() == null ? null : TimeMapper.toOffset(s.lastFailureAt()))
                .downSince(s.downSince() == null ? null : TimeMapper.toOffset(s.downSince()))
                .nextProbeAt(s.nextProbeAt() == null ? null : TimeMapper.toOffset(s.nextProbeAt()))
                .failureCount(s.failureCount()).lastError(s.lastError());
    }
}
