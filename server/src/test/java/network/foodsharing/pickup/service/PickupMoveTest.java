package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.domain.PickupExtraSlot;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.pickup.service.PickupMoveRule.Refusal;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * PU10 (decision 170): the move guards, the regular-occurrence override against the one-time move, and the sign-ups
 * that keep their confirmation while the reminder is reset.
 */
class PickupMoveTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final Instant FROM = NOW.plus(Duration.ofDays(2));
    static final Instant TO = FROM.plus(Duration.ofDays(1)).plus(Duration.ofHours(2));
    static final UUID STORE = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final UUID MEMBER = UUID.randomUUID();

    final PickupScheduleService schedule = mock(PickupScheduleService.class);
    final PickupDateRepository dates = mock(PickupDateRepository.class);
    final PickupSignupRepository signups = mock(PickupSignupRepository.class);
    final PickupExtraSlotRepository extraSlots = mock(PickupExtraSlotRepository.class);
    final StoreService stores = mock(StoreService.class);
    final StorePermissionService rights = mock(StorePermissionService.class);
    final CurrentActorService actor = mock(CurrentActorService.class);
    final HistoryService history = mock(HistoryService.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final Store store = new Store();
    final List<PickupDate> saved = new ArrayList<>();
    PickupSignupService service;

    @BeforeEach
    void setUp() {
        service = new PickupSignupService(schedule, dates, signups, extraSlots, stores, rights,
                mock(UserService.class), actor, history, events, new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC),
                (storeId, userId, at) -> java.util.Map.of());
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Bäckerei");
        when(stores.require(STORE)).thenReturn(store);
        when(stores.lock(STORE)).thenReturn(store);
        when(schedule.zoneOf(store)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(dates.findByStoreIdAndPickupAt(any(), any())).thenReturn(Optional.empty());
        when(dates.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(extraSlots.findByStoreIdAndPickupAt(any(), any())).thenReturn(List.of());
        when(history.event(any(), any())).thenAnswer(i -> HistoryEvent.builder(i.getArgument(0), i.getArgument(1)));
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(MANAGER, UUID.randomUUID(), Role.FOODSAVER,
                "m@test.local"));
        mayEdit(true);
    }

    void mayEdit(boolean value) {
        when(rights.rights(any(SessionPrincipal.class), eq(store))).thenReturn(new StorePermissionService.Rights(true,
                true, true, false, false, false, false, false, true, value, value, false, false, false, false, false,
                false, false, false, value, false, value ? UserTeamStatus.COORDINATOR : UserTeamStatus.MEMBER));
    }

    static PickupSignup signup(UUID userId, boolean confirmed) {
        PickupSignup s = new PickupSignup();
        s.setId(UUID.randomUUID());
        s.setStoreId(STORE);
        s.setUserId(userId);
        s.setPickupAt(FROM);
        s.setConfirmed(confirmed);
        s.setConfirmedBy(confirmed ? MANAGER : null);
        s.setConfirmedAt(confirmed ? NOW.minusSeconds(60) : null);
        s.setRemindedAt(NOW.minusSeconds(30));
        s.setCreatedAt(NOW.minus(Duration.ofDays(1)));
        return s;
    }

    static PickupSchedule.Slot slot(Instant at, int slots, boolean regular, List<PickupSignup> signups) {
        return new PickupSchedule.Slot(STORE, at, slots, "Hintereingang", regular, signups);
    }

    static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    // ------------------------------------------------------------------------------------- the rule

    @Test
    void guardsRunInOrderRightsFoundFutureSameMessageTaken() {
        PickupSchedule.Slot current = slot(FROM, 2, false, List.of());
        assertThat(PickupMoveRule.check(false, current, TO, null, NOW, null)).contains(Refusal.ACCESS_DENIED);
        assertThat(PickupMoveRule.check(true, null, TO, null, NOW, null)).contains(Refusal.NOT_FOUND);
        assertThat(PickupMoveRule.check(true, slot(NOW.minusSeconds(1), 2, false, List.of()), TO, null, NOW, null))
                .contains(Refusal.IN_PAST);
        assertThat(PickupMoveRule.check(true, current, NOW, null, NOW, null)).contains(Refusal.IN_PAST);
        assertThat(PickupMoveRule.check(true, current, null, null, NOW, null)).contains(Refusal.IN_PAST);
        assertThat(PickupMoveRule.check(true, current, FROM, current, NOW, null)).contains(Refusal.SAME_TIME);
        assertThat(PickupMoveRule.check(true, current, TO, null, NOW, "x".repeat(501)))
                .contains(Refusal.MESSAGE_TOO_LONG);
        assertThat(PickupMoveRule.check(true, current, TO, null, NOW, "x".repeat(500))).isEmpty();
        assertThat(PickupMoveRule.check(true, current, TO, slot(TO, 1, true, List.of()), NOW, null))
                .contains(Refusal.TIME_TAKEN);
    }

    @Test
    void aCancelledPickupWithoutSignupsDoesNotTakeTheTime() {
        assertThat(PickupMoveRule.taken(null)).isFalse();
        assertThat(PickupMoveRule.taken(slot(TO, 0, true, List.of()))).isFalse();
        assertThat(PickupMoveRule.taken(slot(TO, 0, false, List.of(signup(MEMBER, false))))).isTrue();
        assertThat(PickupMoveRule.taken(slot(TO, 3, false, List.of()))).isTrue();
    }

    @Test
    void refusalsAnswerWithTheContractCodes() {
        assertThat(Refusal.TIME_TAKEN.exception().getCode()).isEqualTo(ApiErrorCode.PICKUP_TIME_TAKEN);
        assertThat(Refusal.TIME_TAKEN.exception().getStatus().value()).isEqualTo(409);
        assertThat(Refusal.IN_PAST.exception().getCode()).isEqualTo(ApiErrorCode.PICKUP_IN_PAST);
        assertThat(Refusal.SAME_TIME.exception().getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(Refusal.NOT_FOUND.exception().getStatus().value()).isEqualTo(404);
        assertThat(Refusal.ACCESS_DENIED.exception().getStatus().value()).isEqualTo(403);
    }

    @Test
    void theSlotCountGoesAlongNeverBelowTheSignups() {
        assertThat(PickupMoveRule.slotCountAfterMove(slot(FROM, 3, true, List.of()))).isEqualTo(3);
        assertThat(PickupMoveRule.slotCountAfterMove(slot(FROM, 0, false,
                List.of(signup(MEMBER, true), signup(MANAGER, true))))).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------- the service

    @Test
    void aRegularOccurrenceStaysCancelledAndBecomesAOneTimePickupAtTheNewTime() {
        PickupSignup member = signup(MEMBER, true);
        when(schedule.find(store, FROM)).thenReturn(Optional.of(slot(FROM, 3, true, List.of(member))));
        when(schedule.find(store, TO)).thenReturn(Optional.empty(), Optional.of(slot(TO, 3, false, List.of(member))));
        when(signups.findByStoreIdAndPickupAt(STORE, FROM)).thenReturn(List.of(member));

        PickupSchedule.Slot result = service.move(STORE, FROM, TO, "  Neue Zeit  ");

        assertThat(result.pickupAt()).isEqualTo(TO);
        assertThat(saved).anySatisfy(d -> {
            assertThat(d.getPickupAt()).isEqualTo(TO);
            assertThat(d.getSlotCount()).isEqualTo(3);
            assertThat(d.getDescription()).isEqualTo("Hintereingang");
        });
        assertThat(saved).anySatisfy(d -> {
            assertThat(d.getPickupAt()).isEqualTo(FROM);
            assertThat(d.getSlotCount()).isZero();
        });
        verify(dates, never()).delete(any());
        verify(history).record(argThat(e -> e.action() == StoreLogAction.PICKUP_MOVED
                && FROM.toString().equals(e.metadata().get("from")) && TO.toString().equals(e.metadata().get("to"))
                && Integer.valueOf(1).equals(e.metadata().get("signups"))
                && Boolean.TRUE.equals(e.metadata().get("withMessage"))));
        verify(events).publishEvent(new PickupEvents.PickupMovedEvent(STORE, FROM, TO, List.of(MEMBER), MANAGER));
        verify(events).publishEvent(argThat((Object o) -> o instanceof StoreNotifications.Send send
                && send.request().type() == NotificationType.PICKUP_MOVED
                && send.request().recipientIds().equals(java.util.Set.of(MEMBER))
                && "Neue Zeit".equals(send.request().params().get("message"))
                && FROM.toString().equals(send.request().params().get("fromPickupAt"))));
    }

    @Test
    void aOneTimePickupsRowMovesAndTheMoverIsNotNotified() {
        PickupDate old = new PickupDate();
        old.setStoreId(STORE);
        old.setPickupAt(FROM);
        old.setSlotCount(2);
        PickupSignup mine = signup(MANAGER, true);
        when(dates.findByStoreIdAndPickupAt(STORE, FROM)).thenReturn(Optional.of(old));
        when(schedule.find(store, FROM)).thenReturn(Optional.of(slot(FROM, 2, false, List.of(mine))));
        when(schedule.find(store, TO)).thenReturn(Optional.empty(), Optional.of(slot(TO, 2, false, List.of(mine))));
        when(signups.findByStoreIdAndPickupAt(STORE, FROM)).thenReturn(List.of(mine));

        service.move(STORE, FROM, TO, null);

        verify(dates).delete(old);
        assertThat(saved).singleElement().satisfies(d -> assertThat(d.getPickupAt()).isEqualTo(TO));
        verify(events).publishEvent(new PickupEvents.PickupMovedEvent(STORE, FROM, TO, List.of(MANAGER), MANAGER));
        verify(events, never()).publishEvent(argThat((Object o) -> o instanceof StoreNotifications.Send));
    }

    @Test
    void signupsKeepTheirConfirmationTheReminderIsResetExtraSlotsFollow() {
        PickupSignup confirmed = signup(MEMBER, true);
        PickupSignup open = signup(UUID.randomUUID(), false);
        PickupExtraSlot extra = new PickupExtraSlot();
        extra.setPickupAt(FROM);
        when(schedule.find(store, FROM)).thenReturn(Optional.of(slot(FROM, 3, true, List.of(confirmed, open))));
        when(schedule.find(store, TO)).thenReturn(Optional.empty(), Optional.of(slot(TO, 3, false, List.of())));
        when(signups.findByStoreIdAndPickupAt(STORE, FROM)).thenReturn(List.of(confirmed, open));
        when(extraSlots.findByStoreIdAndPickupAt(STORE, FROM)).thenReturn(List.of(extra));

        service.move(STORE, FROM, TO, null);

        assertThat(List.of(confirmed, open)).allSatisfy(s -> {
            assertThat(s.getPickupAt()).isEqualTo(TO);
            assertThat(s.getRemindedAt()).isNull();
        });
        assertThat(confirmed.isConfirmed()).isTrue();
        assertThat(confirmed.getConfirmedBy()).isEqualTo(MANAGER);
        assertThat(confirmed.getConfirmedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(open.isConfirmed()).isFalse();
        assertThat(extra.getPickupAt()).isEqualTo(TO);
    }

    @Test
    void refusedMovesChangeNothing() {
        when(schedule.find(store, FROM)).thenReturn(Optional.of(slot(FROM, 2, false, List.of())));
        when(schedule.find(store, TO)).thenReturn(Optional.of(slot(TO, 2, true, List.of())));
        assertThat(code(() -> service.move(STORE, FROM, TO, null))).isEqualTo(ApiErrorCode.PICKUP_TIME_TAKEN);

        mayEdit(false);
        assertThat(code(() -> service.move(STORE, FROM, TO, null))).isEqualTo(ApiErrorCode.ACCESS_DENIED);

        verify(dates, never()).save(any());
        verify(signups, never()).findByStoreIdAndPickupAt(any(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
