package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.ActorType;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.pickup.domain.PickupAction;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupExtraSlotRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * A23/A24: the automation entry points of the pickups (pickup policy). A confirmation by automation has no confirming
 * person, is done once, and is recorded with actor AUTOMATION; a removal by automation deletes the sign-up, rings the
 * person with bell + mail (reason) and is recorded as AUTO_DECLINED. Both publish the usual pickup events without a
 * person as the actor.
 */
class PickupSignupAutomationTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final Instant PICKUP = NOW.plus(Duration.ofDays(9));
    static final UUID STORE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();

    PickupScheduleService schedule = mock(PickupScheduleService.class);
    PickupSignupRepository signups = mock(PickupSignupRepository.class);
    StoreService stores = mock(StoreService.class);
    HistoryService history = mock(HistoryService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    List<HistoryEvent> recorded = new ArrayList<>();
    Store store = new Store();
    PickupSignup signup = new PickupSignup();
    PickupSignupService service;

    @BeforeEach
    void setUp() {
        service = new PickupSignupService(schedule, mock(PickupDateRepository.class), signups,
                mock(PickupExtraSlotRepository.class), stores, mock(StorePermissionService.class),
                mock(UserService.class), mock(CurrentActorService.class), history, events, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), (storeId, userId, at) -> java.util.Map.of());
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Coop");
        when(stores.require(STORE)).thenReturn(store);
        when(schedule.zoneOf(store)).thenReturn(java.time.ZoneId.of("Europe/Zurich"));
        when(history.event(any(HistoryAction.class), any())).thenAnswer(call -> HistoryEvent.builder(
                call.getArgument(0), call.getArgument(1)));
        when(history.record(any())).thenAnswer(call -> {
            recorded.add(call.getArgument(0));
            return null;
        });
        signup.setStoreId(STORE);
        signup.setUserId(USER);
        signup.setPickupAt(PICKUP);
        when(signups.findByStoreIdAndUserIdAndPickupAt(STORE, USER, PICKUP)).thenReturn(Optional.of(signup));
    }

    private List<Object> published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void anAutomationConfirmsOnceWithoutAConfirmingPerson() {
        assertThat(service.confirmByAutomation(STORE, PICKUP, USER, "pickup-policy", null)).isTrue();
        assertThat(signup.isConfirmed()).isTrue();
        assertThat(signup.getConfirmedBy()).isNull();
        assertThat(signup.getConfirmedAt()).isEqualTo(NOW);
        // already confirmed: nothing happens
        assertThat(service.confirmByAutomation(STORE, PICKUP, USER, "pickup-policy", null)).isFalse();

        assertThat(recorded).extracting(HistoryEvent::actorType).containsOnly(ActorType.AUTOMATION);
        assertThat(recorded).extracting(event -> event.action()).contains(PickupAction.AUTO_CONFIRMED);
        assertThat(published()).filteredOn(PickupEvents.PickupConfirmedEvent.class::isInstance).singleElement()
                .satisfies(event -> assertThat(((PickupEvents.PickupConfirmedEvent) event).byUserId()).isNull());
        assertThat(published()).filteredOn(StoreNotifications.Send.class::isInstance)
                .extracting(event -> ((StoreNotifications.Send) event).request().type())
                .containsExactly(NotificationType.PICKUP_CONFIRMED);
    }

    @Test
    void aMissingSignupIsNeitherConfirmedNorRemoved() {
        when(signups.findByStoreIdAndUserIdAndPickupAt(STORE, USER, PICKUP)).thenReturn(Optional.empty());
        assertThat(service.confirmByAutomation(STORE, PICKUP, USER, "pickup-policy", null)).isFalse();
        assertThat(service.removeByAutomation(STORE, PICKUP, USER, "pickup-policy", "x")).isFalse();
        verify(signups, never()).delete(any());
        assertThat(recorded).isEmpty();
    }

    @Test
    void anAutomationRemovesTheSignupWithBellMailAndReason() {
        assertThat(service.removeByAutomation(STORE, PICKUP, USER, "pickup-policy",
                " Zu wenig Abstand zur letzten Abholung ")).isTrue();
        verify(signups).delete(signup);

        assertThat(recorded).extracting(HistoryEvent::actorType).containsOnly(ActorType.AUTOMATION);
        assertThat(recorded).filteredOn(event -> event.action() == PickupAction.AUTO_DECLINED).singleElement()
                .satisfies(event -> assertThat(event.reason()).isEqualTo("Zu wenig Abstand zur letzten Abholung"));
        assertThat(published()).filteredOn(StoreNotifications.Send.class::isInstance).singleElement()
                .satisfies(event -> {
                    var request = ((StoreNotifications.Send) event).request();
                    assertThat(request.type()).isEqualTo(NotificationType.PICKUP_AUTO_DECLINED);
                    assertThat(request.recipientIds()).containsExactly(USER);
                    assertThat(request.mailTemplate()).isEqualTo("pickup/auto-declined");
                    assertThat(request.params()).containsEntry("reason", "Zu wenig Abstand zur letzten Abholung");
                });
        assertThat(published()).filteredOn(PickupEvents.PickupSignedOutEvent.class::isInstance).singleElement()
                .satisfies(event -> assertThat(((PickupEvents.PickupSignedOutEvent) event).byUserId()).isNull());
    }
}
