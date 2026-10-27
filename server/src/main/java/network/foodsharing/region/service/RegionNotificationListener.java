package network.foodsharing.region.service;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifications of the region module, sent after the triggering transaction committed (docs/conventions.md A12).
 */
@Service
public class RegionNotificationListener {

    private final MembershipService memberships;
    private final RegionRepository regions;
    private final NotificationService notifications;
    private final UserService users;

    public RegionNotificationListener(MembershipService memberships, RegionRepository regions,
                                      NotificationService notifications, UserService users) {
        this.memberships = memberships;
        this.regions = regions;
        this.notifications = notifications;
        this.users = users;
    }

    /**
     * Bell {@code REGION_NEW_MEMBER} to the welcome team (identifier {@code REGION_NEW_MEMBER-<userId>}, removed again
     * when the user is verified).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberJoined(MembershipService.RegionMemberJoined event) {
        Set<UUID> recipients = memberships.welcomeRecipients(event.regionId(), event.userId());
        Region region = regions.findById(event.regionId()).orElse(null);
        if (recipients.isEmpty() || region == null) {
            return;
        }
        String userName = users.findActive(event.userId()).map(user -> user.getDisplayName()).orElse("");
        notifications.notify(NotificationRequest.to(recipients, NotificationType.REGION_NEW_MEMBER)
                .param("regionName", region.getName())
                .param("regionId", region.getId().toString())
                .param("userName", userName)
                .param("userId", event.userId().toString())
                .link("/users/" + event.userId())
                .identifier(BellService.identifier(NotificationType.REGION_NEW_MEMBER, event.userId()))
                .icon("user-plus")
                .build());
    }
}
