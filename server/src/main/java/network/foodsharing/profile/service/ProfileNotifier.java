package network.foodsharing.profile.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import org.springframework.stereotype.Component;

/**
 * Profile bells. {@code BANANA_GIVEN} to the recipient (identifier {@code BANANA_GIVEN-<recipient>-<sender>}, param
 * {@code name} = sender, link {@code /profile/<sender>}); {@code BUDDY_REQUEST} to the asked user (identifier
 * {@code BUDDY_REQUEST-<requester>-<target>}, param {@code name} = requester, link {@code /profile/<requester>}).
 */
@Component
public class ProfileNotifier {

    private final NotificationService notifications;

    public ProfileNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    public static String link(UUID userId) {
        return "/profile/" + userId;
    }

    static String bananaIdentifier(UUID recipientId, UUID senderId) {
        return NotificationType.BANANA_GIVEN.name() + "-" + recipientId + "-" + senderId;
    }

    static String buddyIdentifier(UUID requesterId, UUID targetId) {
        return NotificationType.BUDDY_REQUEST.name() + "-" + requesterId + "-" + targetId;
    }

    void bananaGiven(UUID recipientId, UUID senderId, String senderName) {
        notifications.notify(NotificationRequest.to(List.of(recipientId), NotificationType.BANANA_GIVEN)
                .param("name", senderName).link(link(senderId))
                .identifier(bananaIdentifier(recipientId, senderId)).build());
    }

    void bananaDeleted(UUID recipientId, UUID senderId) {
        notifications.removeBells(bananaIdentifier(recipientId, senderId));
    }

    void buddyRequested(UUID requesterId, String requesterName, UUID targetId) {
        notifications.notify(NotificationRequest.to(List.of(targetId), NotificationType.BUDDY_REQUEST)
                .param("name", requesterName).link(link(requesterId))
                .identifier(buddyIdentifier(requesterId, targetId)).build());
    }

    void buddyRequestDone(UUID requesterId, UUID targetId) {
        notifications.removeBells(buddyIdentifier(requesterId, targetId));
    }
}
