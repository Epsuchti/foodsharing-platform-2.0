package network.foodsharing.store.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Store and pickup bells / mails, always after commit in their own transaction (decision 33): mutating services
 * publish {@link Send} / {@link RemoveBells} events through {@link #send} / {@link #removeBells} instead of calling
 * {@link NotificationService} inside their transaction. Also turns {@link StoreService.TeamReopened} into
 * {@code STORE_TEAM_REOPENED} bells for the region's ambassadors.
 */
@Service
public class StoreNotifications {

    /** A notification to deliver once the publishing transaction has committed. */
    public record Send(NotificationRequest request) {
    }

    /** Bells with this identifier are removed once the publishing transaction has committed. */
    public record RemoveBells(String identifier) {
    }

    private final NotificationService notifications;
    private final StoreRepository stores;
    private final RegionReadService regionReads;

    public StoreNotifications(NotificationService notifications, StoreRepository stores,
                              RegionReadService regionReads) {
        this.notifications = notifications;
        this.stores = stores;
        this.regionReads = regionReads;
    }

    /** Publishes a {@link Send} unless there is nobody to notify. */
    public static void send(ApplicationEventPublisher events, NotificationRequest request) {
        if (!request.recipientIds().isEmpty()) {
            events.publishEvent(new Send(request));
        }
    }

    public static void removeBells(ApplicationEventPublisher events, String identifier) {
        events.publishEvent(new RemoveBells(identifier));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSend(Send event) {
        notifications.notify(event.request());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @NoHistory(reason = "bells are transient hints; the resolving action records its own history")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRemoveBells(RemoveBells event) {
        notifications.removeBells(event.identifier());
    }

    /** CLOSED → OPEN / OPEN_SEARCHING: the region's ambassadors learn that the team is looking for people. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTeamReopened(StoreService.TeamReopened event) {
        stores.findById(event.storeId()).ifPresent(store -> {
            List<UUID> recipients = regionReads.ambassadorsOf(event.regionId()).stream().map(User::getId).toList();
            if (recipients.isEmpty()) {
                return;
            }
            notifications.notify(NotificationRequest.to(recipients, NotificationType.STORE_TEAM_REOPENED)
                    .param("storeId", store.getId().toString()).param("storeName", store.getName())
                    .param("teamStatus", store.getTeamStatus().name()).link("/stores/" + store.getId())
                    .identifier(BellService.identifier(NotificationType.STORE_TEAM_REOPENED, store.getId()))
                    .icon("store").build());
        });
    }
}
