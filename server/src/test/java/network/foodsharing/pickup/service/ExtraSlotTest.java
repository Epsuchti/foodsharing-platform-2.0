package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
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
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupExtraSlot;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Extra slot for the earliest sign-up (AU6, M4 B5): first only, once, future, store switch. */
class ExtraSlotTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    static final Instant PICKUP = NOW.plus(Duration.ofDays(2));
    static final UUID STORE = UUID.randomUUID();
    static final UUID FIRST = UUID.randomUUID();
    static final UUID SECOND = UUID.randomUUID();

    PickupScheduleService schedule = mock(PickupScheduleService.class);
    PickupDateRepository dates = mock(PickupDateRepository.class);
    PickupExtraSlotRepository extraSlots = mock(PickupExtraSlotRepository.class);
    StoreService stores = mock(StoreService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    Store store = new Store();
    List<PickupExtraSlot> saved = new ArrayList<>();
    PickupSignupService service;

    @BeforeEach
    void setUp() {
        service = new PickupSignupService(schedule, dates, mock(PickupSignupRepository.class), extraSlots, stores,
                mock(StorePermissionService.class), mock(UserService.class), actor,
                mock(HistoryService.class, RETURNS_DEEP_STUBS), mock(ApplicationEventPublisher.class),
                new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Bäckerei");
        store.setExtraSlotAllowed(true);
        when(stores.require(STORE)).thenReturn(store);
        when(stores.lock(STORE)).thenReturn(store);
        when(schedule.zoneOf(store)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(schedule.find(eq(store), any())).thenReturn(Optional.of(slot(2)));
        when(dates.findByStoreIdAndPickupAt(any(), any())).thenReturn(Optional.empty());
        when(extraSlots.findByStoreIdAndPickupAt(any(), any())).thenReturn(Optional.empty());
        when(extraSlots.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        as(FIRST);
    }

    void as(UUID userId) {
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(userId, UUID.randomUUID(), Role.FOODSAVER,
                "x@test.local"));
    }

    static PickupSchedule.Slot slot(int total) {
        PickupSignup second = new PickupSignup();
        second.setId(UUID.randomUUID());
        second.setUserId(SECOND);
        second.setCreatedAt(NOW.minus(Duration.ofHours(1)));
        PickupSignup first = new PickupSignup();
        first.setId(UUID.randomUUID());
        first.setUserId(FIRST);
        first.setCreatedAt(NOW.minus(Duration.ofHours(5)));
        return new PickupSchedule.Slot(STORE, PICKUP, total, "desc", true, List.of(second, first));
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
    void earliestSignupAddsOneSlot() {
        service.addExtraSlot(STORE, PICKUP);
        assertThat(saved).singleElement().satisfies(e -> {
            assertThat(e.getUserId()).isEqualTo(FIRST);
            assertThat(e.getPreviousTotalSlots()).isEqualTo(2);
            assertThat(e.getNewTotalSlots()).isEqualTo(3);
        });
        verify(dates).save(any(PickupDate.class));
    }

    @Test
    void onlyTheEarliestSignup() {
        as(SECOND);
        assertThat(code(() -> service.addExtraSlot(STORE, PICKUP))).isEqualTo(ApiErrorCode.NOT_FIRST_SIGNUP);
        assertThat(PickupSignupService.isFirstSignup(slot(2), FIRST)).isTrue();
        assertThat(PickupSignupService.isFirstSignup(slot(2), SECOND)).isFalse();
    }

    @Test
    void onlyOncePerPickup() {
        when(extraSlots.findByStoreIdAndPickupAt(STORE, PICKUP)).thenReturn(Optional.of(new PickupExtraSlot()));
        assertThat(code(() -> service.addExtraSlot(STORE, PICKUP))).isEqualTo(ApiErrorCode.EXTRA_SLOT_ALREADY_ADDED);
    }

    @Test
    void onlyFuturePickupsAndAllowedStores() {
        assertThat(code(() -> service.addExtraSlot(STORE, NOW.minusSeconds(1)))).isEqualTo(ApiErrorCode.PICKUP_IN_PAST);
        store.setExtraSlotAllowed(false);
        assertThat(code(() -> service.addExtraSlot(STORE, PICKUP))).isEqualTo(ApiErrorCode.EXTRA_SLOT_NOT_ALLOWED);
        assertThat(saved).isEmpty();
    }
}
