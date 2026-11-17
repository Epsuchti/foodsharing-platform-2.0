package network.foodsharing.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A25/A26: an outage opens the breaker once and publishes one IntegrationDownEvent; ORGA alerts turn it into an
 * INTEGRATION_DOWN bell + mail for every active ORGA user (nobody when there is no ORGA user).
 */
class OrgaAlertsTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final UserService users = mock(UserService.class);
    private final OrgaAlerts alerts = new OrgaAlerts(notifications, users, mock(PlatformTransactionManager.class));

    @Test
    void aSimulatedOutageIsAnnouncedOncePerOutage() {
        List<Object> events = new ArrayList<>();
        IntegrationRegistry registry = new IntegrationRegistry(new AppProperties(),
                Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC), events::add);
        registry.simulateOutage(Integration.HOLIDAY_CALENDAR);
        registry.simulateOutage(Integration.HOLIDAY_CALENDAR);
        assertThat(events).singleElement().isInstanceOfSatisfying(IntegrationRegistry.IntegrationDownEvent.class,
                event -> assertThat(event.integration()).isEqualTo(Integration.HOLIDAY_CALENDAR));
    }

    @Test
    void everyActiveOrgaUserGetsTheOutageBellWithMail() {
        User orga = new User();
        orga.setId(UUID.randomUUID());
        when(users.findActiveByRole(Role.ORGA)).thenReturn(List.of(orga));

        alerts.onIntegrationDown(new IntegrationRegistry.IntegrationDownEvent(Integration.TELEGRAM, "timeout"));

        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.INTEGRATION_DOWN);
        assertThat(request.getValue().recipientIds()).containsExactly(orga.getId());
        assertThat(request.getValue().mailTemplate()).isEqualTo("notification/INTEGRATION_DOWN");
        assertThat(request.getValue().identifier()).isEqualTo("INTEGRATION_DOWN-TELEGRAM");
        assertThat(request.getValue().params()).containsEntry("integration", "TELEGRAM");
    }

    @Test
    void withoutOrgaUsersNobodyIsRung() {
        when(users.findActiveByRole(Role.ORGA)).thenReturn(List.of());
        alerts.onIntegrationDown(new IntegrationRegistry.IntegrationDownEvent(Integration.SMTP, "down"));
        verify(notifications, never()).notify(any());
    }
}
