package network.foodsharing.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import network.foodsharing.admin.service.AdminOperationsService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.CircuitState;
import network.foodsharing.api.model.Integration;
import network.foodsharing.api.model.IntegrationMode;
import network.foodsharing.api.model.IntegrationTest;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.CircuitBreaker;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.Test;

/** A26: integration status, probe and test message are ORGA only (was AdminOperationsIT). */
class AdminIntegrationsControllerAccessTest {

    AdminOperationsService operations = mock(AdminOperationsService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    AdminIntegrationsController controller = new AdminIntegrationsController(operations, actor);

    @Test
    void probeAndTestAreRefusedBeforeAnythingRunsForNonOrga() {
        when(actor.requireRole(Role.ORGA)).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        assertThatThrownBy(controller::listIntegrations).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.probeIntegration(Integration.GEOCODING)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.testIntegration(Integration.SMTP, new IntegrationTest()))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void orgaProbesAndGetsTheBreakerStateAndMode() {
        when(operations.probe(network.foodsharing.common.integration.Integration.GEOCODING)).thenReturn(
                new CircuitBreaker.Snapshot(network.foodsharing.common.integration.Integration.GEOCODING,
                        network.foodsharing.common.integration.CircuitState.CLOSED, null, null, null, null, 0, null));
        when(operations.mode(any())).thenReturn(IntegrationRegistry.Mode.LOG);
        var status = controller.probeIntegration(Integration.GEOCODING).getBody();
        assertThat(status.getState()).isEqualTo(CircuitState.CLOSED);
        assertThat(status.getMode()).isEqualTo(IntegrationMode.LOG);
    }
}
