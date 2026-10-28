package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reminder window (now + 24 h − 1 h, now + 24 h], disabled flag / inactive users, idempotence via reminded_at. */
class PickupReminderJobTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StoreService stores = mock(StoreService.class);
    UserService users = mock(UserService.class);
    PickupScheduleService schedule = mock(PickupScheduleService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    List<Object> published = new ArrayList<>();
    PickupReminderJob job;
    Store store = new Store();

    @BeforeEach
    void setUp() {
        job = new PickupReminderJob(mock(JobRunner.class), signups, stores, users, schedule, history, published::add, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        store.setId(UUID.randomUUID());
        store.setName("Coop");
        when(stores.liveStores(any())).thenReturn(List.of(store));
        when(schedule.zoneOf(any())).thenReturn(ZoneId.of("Europe/Zurich"));
    }

    private PickupSignup signup(User user) {
        PickupSignup s = new PickupSignup();
        s.setStoreId(store.getId());
        s.setUserId(user.getId());
        s.setPickupAt(NOW.plusSeconds(24 * 3600));
        return s;
    }

    private static User user(boolean enabled, boolean active) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setPickupRemindersEnabled(enabled);
        u.setActive(active);
        return u;
    }

    @Test
    void windowIsTheHourBeforeTheReminderTime() {
        when(signups.findToRemind(any(), any())).thenReturn(List.of());
        assertThat(job.remind()).isZero();
        verify(signups).findToRemind(Instant.parse("2026-11-03T08:00:00Z"), Instant.parse("2026-11-03T09:00:00Z"));
    }

    @Test
    void remindsEnabledActiveUsersAndMarksOnlyTheirSignups() {
        User on = user(true, true);
        User off = user(false, true);
        User gone = user(true, false);
        List<PickupSignup> due = List.of(signup(on), signup(off), signup(gone));
        when(signups.findToRemind(any(), any())).thenReturn(due);
        when(users.findAll(any())).thenReturn(List.of(on, off, gone));

        assertThat(job.remind()).isEqualTo(1);

        assertThat(due.get(0).getRemindedAt()).isEqualTo(NOW);
        // reminders off / inactive: not marked, so re-enabling reminders still sends this pickup's reminder
        assertThat(due.subList(1, 3)).allMatch(s -> s.getRemindedAt() == null);
        assertThat(published).hasSize(1);
        var request = ((StoreNotifications.Send) published.getFirst()).request();
        assertThat(request.type()).isEqualTo(NotificationType.PICKUP_REMINDER);
        assertThat(request.recipientIds()).containsExactly(on.getId());
        assertThat(request.mailTemplate()).isEqualTo("pickup/reminder");
        assertThat(request.params()).containsEntry("when", "03.11.2026 10:00");
    }
}
