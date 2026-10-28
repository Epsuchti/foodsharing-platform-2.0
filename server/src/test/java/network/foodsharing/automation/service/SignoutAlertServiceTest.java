package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.CancellationStatus;
import network.foodsharing.automation.domain.SignoutAlertSetting;
import network.foodsharing.automation.domain.SignoutCancellation;
import network.foodsharing.automation.repository.SignoutAlertSettingRepository;
import network.foodsharing.automation.repository.SignoutCancellationRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.telegram.service.TelegramChatService;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Sign-out alerts (AU5): cutoff, latest wins, rejoined, outcomes, once per day (M4 B5). */
class SignoutAlertServiceTest {

    static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    static final Instant PICKUP = NOW.plus(Duration.ofHours(20));
    static final UUID STORE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    SignoutAlertSettingRepository settings = mock(SignoutAlertSettingRepository.class);
    SignoutCancellationRepository cancellations = mock(SignoutCancellationRepository.class);
    PickupScheduleService schedule = mock(PickupScheduleService.class);
    StoreService stores = mock(StoreService.class);
    NotificationService notifications = mock(NotificationService.class);
    TelegramChatService telegram = mock(TelegramChatService.class);
    AppProperties properties = new AppProperties();
    SignoutAlertSetting setting = new SignoutAlertSetting();
    Store store = new Store();
    List<SignoutCancellation> saved = new ArrayList<>();
    Instant now = NOW;

    SignoutAlertService service(Instant at) {
        return new SignoutAlertService(settings, cancellations, mock(PickupPolicyService.class), schedule, stores,
                mock(UserService.class), notifications, telegram, mock(HistoryService.class, RETURNS_DEEP_STUBS),
                properties, JsonMapper.builder().build(), Clock.fixed(at, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
    }

    @BeforeEach
    void setUp() {
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Bäckerei");
        setting.setId(UUID.randomUUID());
        setting.setStoreId(STORE);
        setting.setEnabled(true);
        setting.setCutoffEnabled(true);
        setting.setMaximumHoursBeforePickup(24);
        setting.setChannels("BELL,TELEGRAM");
        setting.setActiveSince(NOW.minus(Duration.ofDays(1)));
        when(settings.findByStoreId(STORE)).thenReturn(Optional.of(setting));
        when(settings.findById(setting.getId())).thenReturn(Optional.of(setting));
        when(settings.findByEnabledTrue()).thenReturn(List.of(setting));
        when(stores.liveStores(any())).thenReturn(List.of(store));
        when(stores.managerIds(STORE)).thenReturn(List.of(MANAGER, USER));
        when(schedule.zoneOf(store)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(schedule.find(eq(store), any())).thenReturn(Optional.of(slot(2)));
        when(cancellations.findByStoreIdAndPickupAtAndUserId(any(), any(), any())).thenAnswer(i ->
                saved.stream().filter(c -> c.getUserId().equals(i.getArgument(2))).findFirst());
        when(cancellations.save(any())).thenAnswer(i -> {
            SignoutCancellation c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
                saved.add(c);
            }
            return c;
        });
    }

    static PickupSchedule.Slot slot(int total, UUID... users) {
        List<PickupSignup> signups = new ArrayList<>();
        for (UUID user : users) {
            PickupSignup s = new PickupSignup();
            s.setUserId(user);
            signups.add(s);
        }
        return new PickupSchedule.Slot(STORE, PICKUP, total, null, true, signups);
    }

    PickupEvents.PickupSignedOutEvent signOut(Instant at, UUID by) {
        return new PickupEvents.PickupSignedOutEvent(STORE, PICKUP, USER, by, at);
    }

    @Test
    void ownSignOutWithinTheCutoffAlertsTheManagers() {
        assertThat(service(NOW).recordCancellation(signOut(NOW, USER))).isTrue();
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.PICKUP_SIGNOUT_ALERT);
        assertThat(request.getValue().recipientIds()).containsExactly(MANAGER);
        verify(telegram).sendForStore(eq(STORE), any(), anyCollection(), anyString(), anyString());
        assertThat(saved).singleElement().satisfies(c -> {
            assertThat(c.getStatus()).isEqualTo(CancellationStatus.OPEN);
            assertThat(c.getImmediateNotifiedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void removalByAManagerIsNoCancellation() {
        service(NOW).onSignedOut(signOut(NOW, MANAGER));
        verify(settings, never()).findByStoreId(any());
        service(NOW).onSignedOut(signOut(NOW, USER));
        assertThat(saved).hasSize(1);
    }

    @Test
    void signOutBeforeTheCutoffIsIgnored() {
        assertThat(service(NOW).recordCancellation(signOut(PICKUP.minus(Duration.ofHours(30)), USER))).isFalse();
        setting.setCutoffEnabled(false);
        assertThat(service(NOW).recordCancellation(signOut(PICKUP.minus(Duration.ofHours(30)), USER))).isTrue();
    }

    @Test
    void pickupBeforeActiveSinceIsIgnored() {
        setting.setActiveSince(PICKUP.plusSeconds(1));
        assertThat(service(NOW).recordCancellation(signOut(NOW, USER))).isFalse();
    }

    @Test
    void latestSignOutWins() {
        SignoutAlertService service = service(NOW);
        service.recordCancellation(signOut(NOW, USER));
        saved.getFirst().setCancellerRejoined(true);
        service.recordCancellation(signOut(NOW.plus(Duration.ofHours(2)), USER));
        assertThat(saved).singleElement().satisfies(c -> {
            assertThat(c.getCancelledAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
            assertThat(c.isCancellerRejoined()).isFalse();
        });
    }

    @Test
    void outcomes() {
        assertThat(SignoutAlertService.outcome(slot(2, USER), USER)).isEqualTo(CancellationStatus.REJOINED);
        assertThat(SignoutAlertService.outcome(slot(2, MANAGER), USER)).isEqualTo(CancellationStatus.NOT_REPLACED);
        assertThat(SignoutAlertService.outcome(slot(1, MANAGER), USER)).isEqualTo(CancellationStatus.REPLACED);
        assertThat(SignoutAlertService.outcome(null, USER)).isEqualTo(CancellationStatus.UNKNOWN);
    }

    @Test
    void dailyCheckReportsNotReplacedOncePerDay() {
        SignoutCancellation open = new SignoutCancellation();
        open.setId(UUID.randomUUID());
        open.setStoreId(STORE);
        open.setPickupAt(PICKUP);
        open.setUserId(USER);
        open.setCancelledAt(PICKUP.minus(Duration.ofHours(5)));
        open.setStatus(CancellationStatus.OPEN);
        when(cancellations.findByStoreIdAndStatusAndPickupAtLessThanEqual(eq(STORE), eq(CancellationStatus.OPEN),
                any())).thenReturn(List.of(open));
        when(schedule.find(eq(store), any())).thenReturn(Optional.of(slot(2, MANAGER)));
        Instant afterPickup = Instant.parse("2026-11-11T05:00:00Z"); // 06:00 in Zurich
        assertThat(service(afterPickup).runDaily()).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(CancellationStatus.NOT_REPLACED);
        assertThat(open.getFinalNotifiedAt()).isEqualTo(afterPickup);
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.PICKUP_SIGNOUT_NOT_REPLACED);
        assertThat(request.getValue().mailTemplate()).isEqualTo("notification/PICKUP_SIGNOUT_NOT_REPLACED");
        assertThat(setting.getLastCheckedAt()).isEqualTo(afterPickup);
        assertThat(service(afterPickup.plus(Duration.ofHours(3))).runDaily()).isZero();
    }

    @Test
    void dailyCheckWaitsForTheConfiguredLocalTime() {
        Instant early = Instant.parse("2026-11-11T01:00:00Z"); // 02:00 in Zurich
        assertThat(service(early).runDaily()).isZero();
        verify(cancellations, never()).findByStoreIdAndStatusAndPickupAtLessThanEqual(any(), any(), any());
    }
}
