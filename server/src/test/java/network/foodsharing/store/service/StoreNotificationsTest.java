package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** After-commit delivery (decision 33) and the STORE_TEAM_REOPENED bell to the region's ambassadors. */
class StoreNotificationsTest {

    NotificationService notifications = mock(NotificationService.class);
    StoreRepository stores = mock(StoreRepository.class);
    RegionReadService regionReads = mock(RegionReadService.class);
    StoreNotifications listener = new StoreNotifications(notifications, stores, regionReads);

    @Test
    void teamReopenedGoesToAmbassadors() {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setRegionId(UUID.randomUUID());
        store.setName("Coop");
        store.setTeamStatus(TeamSearchStatus.OPEN_SEARCHING);
        User ambassador = new User();
        ambassador.setId(UUID.randomUUID());
        when(stores.findById(store.getId())).thenReturn(Optional.of(store));
        when(regionReads.ambassadorsOf(store.getRegionId())).thenReturn(List.of(ambassador));

        listener.onTeamReopened(new StoreService.TeamReopened(store.getId(), store.getRegionId()));

        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(NotificationType.STORE_TEAM_REOPENED);
        assertThat(sent.getValue().recipientIds()).containsExactly(ambassador.getId());
        assertThat(sent.getValue().params()).containsEntry("storeName", "Coop")
                .containsEntry("teamStatus", "OPEN_SEARCHING");
    }

    @Test
    void noAmbassadorsNoBell() {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        when(stores.findById(store.getId())).thenReturn(Optional.of(store));
        listener.onTeamReopened(new StoreService.TeamReopened(store.getId(), UUID.randomUUID()));
        verify(notifications, never()).notify(any());
    }

    @Test
    void emptyRecipientListsAreNotPublished() {
        List<Object> published = new ArrayList<>();
        StoreNotifications.send(published::add,
                NotificationRequest.to(List.of(), NotificationType.STORE_DELETED).build());
        assertThat(published).isEmpty();
    }
}
