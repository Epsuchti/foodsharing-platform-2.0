package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/** Nightly store maintenance (§6.3, M4 B5): unconfirmed reminder, empty tomorrow, inactive managers. */
class StoreMaintenanceJobTest {

    static final Instant NOW = Instant.parse("2026-11-10T01:30:00Z");
    static final UUID A = UUID.randomUUID();
    static final UUID B = UUID.randomUUID();

    StoreService stores = mock(StoreService.class);
    PickupScheduleService schedule = mock(PickupScheduleService.class);
    PickupAutomationQueries pickups = mock(PickupAutomationQueries.class);
    UserService users = mock(UserService.class);
    NotificationService notifications = mock(NotificationService.class);
    Store store = new Store();
    User active = user(A, NOW.minus(Duration.ofDays(2)));
    User inactive = user(B, NOW.minus(Duration.ofDays(100)));
    StoreMaintenanceJob job;

    static User user(UUID id, Instant lastLogin) {
        User user = new User();
        user.setId(id);
        user.setDisplayName("M" + id.toString().substring(0, 4));
        user.setLastLoginAt(lastLogin);
        return user;
    }

    @BeforeEach
    void setUp() {
        job = new StoreMaintenanceJob(mock(JobRunner.class), stores, schedule, pickups, users, notifications,
                new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
        store.setId(UUID.randomUUID());
        store.setName("Bäckerei");
        when(stores.managerIds(store.getId())).thenReturn(List.of(A, B));
        when(users.findAll(any())).thenReturn(List.of(active, inactive));
        when(schedule.zoneOf(store)).thenReturn(ZoneId.of("Europe/Zurich"));
    }

    List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, org.mockito.Mockito.atLeast(0)).notify(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void remindsAboutUnconfirmedSignupsWithin48Hours() {
        PickupSignup soon = new PickupSignup();
        soon.setPickupAt(NOW.plus(Duration.ofHours(20)));
        PickupSignup later = new PickupSignup();
        later.setPickupAt(NOW.plus(Duration.ofHours(60)));
        when(pickups.unconfirmedFuture(any(), eq(NOW))).thenReturn(List.of(soon, later));
        when(notifications.sentSince(any(), any())).thenReturn(true);
        assertThat(job.maintain(store, NOW)).isEqualTo(1);
        NotificationRequest request = sent().getFirst();
        assertThat(request.type()).isEqualTo(NotificationType.PICKUP_UNCONFIRMED_REMINDER);
        assertThat(request.params()).containsEntry("count", "1");
        assertThat(request.recipientIds()).containsExactlyInAnyOrder(A, B);
    }

    @Test
    void emptyPickupTomorrowAndInactiveManager() {
        Instant tomorrow = Instant.parse("2026-11-11T17:00:00Z");
        when(schedule.schedule(eq(store), any(), any())).thenReturn(List.of(
                new PickupSchedule.Slot(store.getId(), tomorrow, 2, null, true, List.of())));
        assertThat(job.maintain(store, NOW)).isEqualTo(2);
        List<NotificationRequest> requests = sent();
        assertThat(requests).extracting(NotificationRequest::type).containsExactly(
                NotificationType.PICKUP_EMPTY_TOMORROW, NotificationType.STORE_MANAGER_INACTIVE);
        assertThat(requests.get(1).recipientIds()).containsExactly(A);
    }

    @Test
    void inactiveManagerOnlyOncePer30Days() {
        when(notifications.sentSince(eq("STORE_MANAGER_INACTIVE-" + store.getId() + "-" + B), any())).thenReturn(true);
        assertThat(job.maintain(store, NOW)).isZero();
        verify(notifications, never()).notify(any());
    }
}
