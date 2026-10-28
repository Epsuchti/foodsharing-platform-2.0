package network.foodsharing.poll.service;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.common.realtime.RealtimeEvent;
import network.foodsharing.common.realtime.RealtimeEventType;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.poll.domain.Poll;
import org.springframework.stereotype.Component;

/**
 * Poll bells and realtime (B7). Identifiers {@code <TYPE>-<poll>} for POLL_NEW, POLL_STARTED, POLL_ENDING_SOON and
 * POLL_CANCELLED; params {@code name}, {@code endsAt} (ISO instant); link {@code /polls/<id>}. Realtime
 * {@code POLL_STATE} {@code {pollId, voteCount}} to the author after each vote.
 */
@Component
public class PollNotifier {

    static final NotificationType[] OPEN_TYPES = {NotificationType.POLL_NEW, NotificationType.POLL_STARTED,
            NotificationType.POLL_ENDING_SOON};

    private final NotificationService notifications;
    private final RealtimePublisher realtime;
    private final Clock clock;

    public PollNotifier(NotificationService notifications, RealtimePublisher realtime, Clock clock) {
        this.notifications = notifications;
        this.realtime = realtime;
        this.clock = clock;
    }

    public static String link(UUID pollId) {
        return "/polls/" + pollId;
    }

    static String identifier(NotificationType type, UUID pollId) {
        return type.name() + "-" + pollId;
    }

    void notify(Poll poll, NotificationType type, Collection<UUID> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        notifications.notify(NotificationRequest.to(recipients, type).param("name", poll.getName())
                .param("endsAt", poll.getEndsAt().toString()).link(link(poll.getId()))
                .identifier(identifier(type, poll.getId())).build());
    }

    /** Were voters notified about this poll at all (POLL_NEW or POLL_STARTED)? */
    boolean votersNotified(Poll poll) {
        return (poll.getStartNotifiedAt() != null && !poll.getStartNotifiedAt().isBefore(poll.getStartsAt()))
                || notifications.sentSince(identifier(NotificationType.POLL_NEW, poll.getId()), java.time.Instant.EPOCH);
    }

    void removeOpenBells(UUID pollId) {
        for (NotificationType type : OPEN_TYPES) {
            notifications.removeBells(identifier(type, pollId));
        }
    }

    void removeOpenBells(UUID pollId, UUID userId) {
        for (NotificationType type : OPEN_TYPES) {
            notifications.removeBells(identifier(type, pollId), userId);
        }
    }

    void voteCast(Poll poll, int voteCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pollId", poll.getId().toString());
        payload.put("voteCount", voteCount);
        realtime.toUsers(Set.of(poll.getAuthorId()), new RealtimeEvent(RealtimeEventType.POLL_STATE, clock.instant(),
                payload));
    }
}
