package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.fsp.service.FspCleaningQueries;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Pickups seen from a user (PU6–PU8, B5; was PickupUserApiIT): past pickups and "sign out of all future pickups" only
 * for the person themself or a user administrator; the store history needs maySeePickupHistory and covers at most 366
 * days; pickup options are for verified foodsavers (the option rules are PickupOptionsRuleTest).
 */
class PickupUserServiceAccessTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID ME = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final UUID STORE = UUID.randomUUID();
    static final SessionPrincipal PRINCIPAL = new SessionPrincipal(ME, UUID.randomUUID(), Role.FOODSAVER,
            "me@test.local");

    PickupScheduleService schedule = mock(PickupScheduleService.class);
    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StoreService stores = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    PermissionService permissions = mock(PermissionService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Store store = new Store();
    PickupUserService service;

    @BeforeEach
    void setUp() {
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        when(stores.require(STORE)).thenReturn(store);
        when(actor.requirePrincipal()).thenReturn(PRINCIPAL);
        service = new PickupUserService(schedule, signups, stores, rights, permissions, mock(UserService.class),
                mock(RegionService.class), mock(IntroPickupBookingService.class), actor, history,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(PickupExtraSlotRepository.class), new AppProperties(),
                mock(FspCleaningQueries.class), events);
    }

    static ApiException error(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex;
        }
        throw new AssertionError("no ApiException");
    }

    static StorePermissionService.Rights historyRights(boolean maySeeHistory) {
        return new StorePermissionService.Rights(true, true, true, false, false, false, false, false, true, false,
                false, maySeeHistory, false, false, false, true, true, false, false, false, false,
                UserTeamStatus.MEMBER);
    }

    @Test
    void pastPickupsOfSomebodyElseNeedUserAdministration() {
        assertThat(error(() -> service.pastPickups(OTHER, 0, 20)).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(signups, never()).findByUserIdAndPickupAtLessThanOrderByPickupAtDescIdDesc(any(), any(), any());
        when(permissions.mayAdministrateUser(PRINCIPAL, OTHER)).thenReturn(true);
        when(signups.findByUserIdAndPickupAtLessThanOrderByPickupAtDescIdDesc(eq(OTHER), eq(NOW), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        assertThat(service.pastPickups(OTHER, 0, 20).getContent()).isEmpty();
    }

    @Test
    void ownPastPickupsNeedNoRight() {
        when(signups.findByUserIdAndPickupAtLessThanOrderByPickupAtDescIdDesc(eq(ME), eq(NOW), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        assertThat(service.pastPickups(ME, null, null).getContent()).isEmpty();
    }

    @Test
    void signingSomebodyElseOutOfAllFuturePickupsNeedsUserAdministration() {
        assertThat(error(() -> service.deleteAllFuture(OTHER, "x")).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(signups, never()).deleteAll(anyList());
    }

    @Test
    void signingOutOfAllFuturePickupsLogsPerStoreWithTheReasonAndTellsTheLinkedEinAbs() {
        PickupSignup signup = new PickupSignup();
        signup.setStoreId(STORE);
        signup.setUserId(ME);
        signup.setPickupAt(NOW.plusSeconds(86400));
        when(signups.findByUserIdAndPickupAtGreaterThanEqualOrderByPickupAtAscIdAsc(ME, NOW))
                .thenReturn(List.of(signup));
        when(stores.storesById(anyList())).thenReturn(List.of(store));
        assertThat(service.deleteAllFuture(ME, "  Ferien ")).isEqualTo(1);
        verify(signups).deleteAll(List.of(signup));
        verify(history).event(StoreLogAction.SIGN_OUT_SLOT, STORE);
        verify(history).event(StoreLogAction.ALL_FUTURE_PICKUPS_DELETED, STORE);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(PickupEvents.PickupSignupsDeletedEvent.class, e -> {
            assertThat(e.userId()).isEqualTo(ME);
            assertThat(e.reason()).isEqualTo(PickupEvents.SignupsDeletedReason.ALL_FUTURE_DELETED);
            assertThat(e.signups()).containsExactly(new PickupEvents.DeletedSignup(STORE, signup.getPickupAt()));
        });
    }

    @Test
    void theStoreHistoryNeedsTheRightAndAtMostAYear() {
        when(rights.rights(PRINCIPAL, store)).thenReturn(historyRights(false));
        assertThat(error(() -> service.storeHistory(STORE, null, null, 0, 20)).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        when(rights.rights(PRINCIPAL, store)).thenReturn(historyRights(true));
        assertThat(error(() -> service.storeHistory(STORE, Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"), 0, 20)).getCode()).isEqualTo(ApiErrorCode.RANGE_TOO_LARGE);
        assertThat(error(() -> service.storeHistory(STORE, NOW, NOW.minusSeconds(60), 0, 20)).getCode())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        when(schedule.schedule(eq(store), any(), any())).thenReturn(List.of(
                new PickupSchedule.Slot(STORE, NOW.minusSeconds(7200), 2, null, false, List.of(new PickupSignup())),
                new PickupSchedule.Slot(STORE, NOW.minusSeconds(3600), 2, null, false, List.of())));
        var page = service.storeHistory(STORE, null, null, 0, 20);
        assertThat(page.getContent()).as("only pickups with sign-ups").singleElement()
                .satisfies(slot -> assertThat(slot.pickupAt()).isEqualTo(NOW.minusSeconds(7200)));
    }

    @Test
    void pickupOptionsAreForVerifiedFoodsaversOnly() {
        User foodsharer = new User();
        foodsharer.setId(ME);
        foodsharer.setRole(Role.FOODSHARER);
        foodsharer.setVerified(true);
        when(actor.requireUser()).thenReturn(foodsharer);
        assertThat(error(() -> service.options()).getCode()).isEqualTo(ApiErrorCode.NOT_VERIFIED);
        User unverified = new User();
        unverified.setId(ME);
        unverified.setRole(Role.FOODSAVER);
        unverified.setVerified(false);
        when(actor.requireUser()).thenReturn(unverified);
        assertThat(error(() -> service.options()).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(stores, never()).teamStoresOf(any());
    }
}
