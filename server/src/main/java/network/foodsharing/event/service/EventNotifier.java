package network.foodsharing.event.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.event.domain.Event;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import org.springframework.stereotype.Component;

/**
 * Event bells (B7). Identifiers: {@code EVENT_INVITATION-<event>}, {@code EVENT_UPDATED-<event>} and
 * {@code EVENT_POST-<event>} (the latter two replaced on every change, so one per event); params {@code name},
 * {@code startsAt} (ISO instant; {@code author} for EVENT_POST); link {@code /events/<id>}.
 */
@Component
public class EventNotifier {

    private final NotificationService notifications;

    public EventNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    public static String link(UUID eventId) {
        return "/events/" + eventId;
    }

    void invited(Event event, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        notifications.notify(request(userIds, NotificationType.EVENT_INVITATION, event).build());
    }

    void updated(Event event, Collection<UUID> participants) {
        notifications.removeBells(NotificationType.EVENT_UPDATED.name() + "-" + event.getId());
        if (participants.isEmpty()) {
            return;
        }
        notifications.notify(request(participants, NotificationType.EVENT_UPDATED, event).build());
    }

    void wallPost(Event event, Collection<UUID> recipients, UUID posterId, String posterName) {
        Set<UUID> to = new LinkedHashSet<>(recipients);
        to.remove(posterId);
        notifications.removeBells(NotificationType.EVENT_POST.name() + "-" + event.getId());
        if (to.isEmpty()) {
            return;
        }
        notifications.notify(request(to, NotificationType.EVENT_POST, event).param("author", posterName).build());
    }

    void removed(UUID eventId) {
        for (NotificationType type : new NotificationType[] {NotificationType.EVENT_INVITATION,
                NotificationType.EVENT_UPDATED, NotificationType.EVENT_POST}) {
            notifications.removeBells(type.name() + "-" + eventId);
        }
    }

    private static NotificationRequest.Builder request(Collection<UUID> to, NotificationType type, Event event) {
        return NotificationRequest.to(to, type).param("name", event.getName())
                .param("startsAt", event.getStartsAt().toString()).link(link(event.getId()))
                .identifier(type.name() + "-" + event.getId());
    }
}
