package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * PU2 slot edits and PU3 sign-up confirmation (rules the e2e specs pu2-one-time / pu3-signup asserted): a pickup never
 * gets fewer slots than sign-ups, 0 slots cancel a one-time pickup, store managers (COORDINATOR) are pre-confirmed,
 * everybody else waits for the managers, who get the PICKUP_SIGNUP_UNCONFIRMED bell.
 */
class PickupSignupEditAndConfirmTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final Instant PICKUP = NOW.plus(Duration.ofDays(3));
    static final UUID STORE = UUID.randomUUID();
    static final UUID ME = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();

    PickupScheduleService schedule = mock(PickupScheduleService.class);
    PickupDateRepository dates = mock(PickupDateRepository.class);
    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StoreService stores = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Store store = new Store();
    PickupSignupService service;

    @BeforeEach
    void setUp() {
        service = new PickupSignupService(schedule, dates, signups, mock(PickupExtraSlotRepository.class), stores,
                rights, mock(UserService.class), actor, mock(HistoryService.class, RETURNS_DEEP_STUBS), events,
                new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC), (storeId, userId, at) -> java.util.Map.of());
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Coop");
        when(stores.require(STORE)).thenReturn(store);
        when(stores.lock(STORE)).thenReturn(store);
        when(schedule.zoneOf(store)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(dates.findByStoreIdAndPickupAt(any(), any())).thenReturn(Optional.empty());
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(ME, UUID.randomUUID(), Role.FOODSAVER,
                "me@test.local"));
        when(rights.rights(any(SessionPrincipal.class), eq(store))).thenReturn(allRights());
    }

    static StorePermissionService.Rights allRights() {
        return new StorePermissionService.Rights(true, true, true, true, true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, false, true, true, UserTeamStatus.COORDINATOR);
    }

    static PickupSchedule.Slot slot(int slots, int signedUp) {
        List<PickupSignup> list = new ArrayList<>();
        for (int i = 0; i < signedUp; i++) {
            PickupSignup s = new PickupSignup();
            s.setUserId(UUID.randomUUID());
            s.setPickupAt(PICKUP);
            list.add(s);
        }
        return new PickupSchedule.Slot(STORE, PICKUP, slots, null, false, list);
    }

    static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    @Test
    void fewerSlotsThanSignupsAreRefusedAndNothingIsSaved() {
        when(schedule.find(eq(store), eq(PICKUP))).thenReturn(Optional.of(slot(3, 2)));
        assertThat(code(() -> service.edit(STORE, PICKUP, 1, null))).isEqualTo(ApiErrorCode.MORE_OCCUPIED_SLOTS);
        verify(dates, never()).save(any());
        // as many slots as sign-ups is fine
        assertThat(code(() -> service.edit(STORE, PICKUP, 2, null))).isNull();
    }

    @Test
    void zeroSlotsCancelAOneTimePickupWithoutSignups() {
        when(schedule.find(eq(store), eq(PICKUP))).thenReturn(Optional.of(slot(4, 0)));
        service.edit(STORE, PICKUP, 0, "Inventur");
        ArgumentCaptor<PickupDate> saved = ArgumentCaptor.forClass(PickupDate.class);
        verify(dates).save(saved.capture());
        assertThat(saved.getValue().getSlotCount()).isZero();
        assertThat(saved.getValue().getPickupAt()).isEqualTo(PICKUP);
    }

    @Test
    void pastPickupsAndOutOfRangeSlotCountsAreRefused() {
        assertThat(code(() -> service.edit(STORE, NOW.minusSeconds(60), 2, null)))
                .isEqualTo(ApiErrorCode.PICKUP_IN_PAST);
        assertThat(code(() -> service.edit(STORE, PICKUP, -1, null))).isEqualTo(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
    }

    private void eligibleSlot() {
        when(schedule.find(eq(store), eq(PICKUP))).thenReturn(Optional.of(slot(3, 0)));
        PickupScheduleService.EligibilityContext context = mock(PickupScheduleService.EligibilityContext.class);
        when(context.evaluate(any(), any())).thenReturn(PickupEligibilityRules.combine(List.of(), List.of(), null));
        when(schedule.context(store, ME)).thenReturn(context);
        when(stores.managerIds(STORE)).thenReturn(List.of(MANAGER, ME));
    }

    @Test
    void storeManagersArePreConfirmedAndNoManagerIsBelled() {
        eligibleSlot();
        when(rights.teamStatus(ME, STORE)).thenReturn(UserTeamStatus.COORDINATOR);
        assertThat(service.signUp(STORE, PICKUP, List.of())).isTrue();
        ArgumentCaptor<PickupSignup> saved = ArgumentCaptor.forClass(PickupSignup.class);
        verify(signups).save(saved.capture());
        assertThat(saved.getValue().isConfirmed()).isTrue();
        assertThat(saved.getValue().getConfirmedBy()).isEqualTo(ME);
        verify(events, never()).publishEvent(any(StoreNotifications.Send.class));
    }

    @Test
    void membersWaitForTheManagersWhoAreBelledWithoutTheSignerThemself() {
        eligibleSlot();
        when(rights.teamStatus(ME, STORE)).thenReturn(UserTeamStatus.MEMBER);
        assertThat(service.signUp(STORE, PICKUP, List.of())).isFalse();
        ArgumentCaptor<PickupSignup> saved = ArgumentCaptor.forClass(PickupSignup.class);
        verify(signups).save(saved.capture());
        assertThat(saved.getValue().isConfirmed()).isFalse();
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(published.capture());
        List<StoreNotifications.Send> bells = published.getAllValues().stream()
                .filter(StoreNotifications.Send.class::isInstance).map(StoreNotifications.Send.class::cast).toList();
        assertThat(bells).hasSize(1);
        assertThat(bells.getFirst().request().type()).isEqualTo(NotificationType.PICKUP_SIGNUP_UNCONFIRMED);
        assertThat(bells.getFirst().request().recipientIds()).isEqualTo(Set.of(MANAGER));
    }
}
