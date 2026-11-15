package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.pickup.repository.RegularPickupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** PU1 (e2e pu1-regular): a changed weekly schedule tells the team except the manager who changed it, once. */
class RegularPickupReplaceTest {

    static final UUID STORE = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final UUID MEMBER = UUID.randomUUID();

    RegularPickupRepository regular = mock(RegularPickupRepository.class);
    StoreService stores = mock(StoreService.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    CurrentActorService actor = mock(CurrentActorService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    RegularPickupService service;

    @BeforeEach
    void setUp() {
        service = new RegularPickupService(regular, stores, rights, actor, mock(HistoryService.class, RETURNS_DEEP_STUBS),
                events, new AppProperties());
        Store store = new Store();
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setName("Coop");
        when(stores.require(STORE)).thenReturn(store);
        when(stores.teamIds(STORE)).thenReturn(List.of(MANAGER, MEMBER));
        when(actor.requirePrincipal()).thenReturn(new SessionPrincipal(MANAGER, UUID.randomUUID(), Role.FOODSAVER,
                "bieb@test.local"));
        when(rights.rights(any(SessionPrincipal.class), eq(store))).thenReturn(PickupSignupEditAndConfirmTest.allRights());
        when(regular.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(regular.findByStoreId(STORE)).thenReturn(RegularPickupService.validate(STORE,
                List.of(new RegularPickupService.Entry(DayOfWeek.MONDAY, "19:00", 3, null)), 50));
    }

    @Test
    void aChangedScheduleTellsTheTeamWithoutTheManager() {
        service.replace(STORE, List.of(new RegularPickupService.Entry(DayOfWeek.MONDAY, "19:00", 3, null),
                new RegularPickupService.Entry(DayOfWeek.TUESDAY, "19:00", 3, null)));
        ArgumentCaptor<StoreNotifications.Send> sent = ArgumentCaptor.forClass(StoreNotifications.Send.class);
        verify(events).publishEvent(sent.capture());
        assertThat(sent.getValue().request().type()).isEqualTo(NotificationType.STORE_REGULAR_PICKUP_CHANGED);
        assertThat(sent.getValue().request().recipientIds()).isEqualTo(Set.of(MEMBER));
        verify(regular).deleteByStoreId(STORE);
    }

    @Test
    void anUnchangedScheduleChangesNothingAndTellsNobody() {
        service.replace(STORE, List.of(new RegularPickupService.Entry(DayOfWeek.MONDAY, " 19:00 ", 3, null)));
        verify(events, never()).publishEvent(any());
        verify(regular, never()).deleteByStoreId(any());
    }
}
