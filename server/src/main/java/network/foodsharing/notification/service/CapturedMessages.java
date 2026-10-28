package network.foodsharing.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import network.foodsharing.notification.domain.NotificationChannelType;
import org.springframework.stereotype.Component;

/**
 * Push and Telegram messages of the log-only clients ({@code app.push.mode=log}, {@code app.telegram.mode=log}),
 * kept in memory for {@code GET /api/dev/notifications?channel=PUSH|TELEGRAM} (A23). Newest first, at most 1000.
 */
@Component
public class CapturedMessages {

    private static final int MAX = 1000;

    /** One captured message; {@code target} = push endpoint or Telegram chat id. */
    public record Captured(UUID id, UUID userId, String type, NotificationChannelType channel, Map<String, String> params,
                           String link, String identifier, String title, String body, String target,
                           Instant createdAt) {
    }

    private final Deque<Captured> messages = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    public CapturedMessages(Clock clock) {
        this.clock = clock;
    }

    public Captured add(UUID userId, String type, NotificationChannelType channel, Map<String, String> params,
                        String link, String identifier, String title, String body, String target) {
        Captured captured = new Captured(UUID.randomUUID(), userId, type, channel,
                params == null ? Map.of() : Map.copyOf(params), link, identifier, title, body, target, clock.instant());
        messages.addFirst(captured);
        while (messages.size() > MAX) {
            messages.pollLast();
        }
        return captured;
    }

    public List<Captured> list(UUID userId, String type, NotificationChannelType channel) {
        List<Captured> result = new ArrayList<>();
        for (Captured captured : messages) {
            if ((userId == null || userId.equals(captured.userId())) && (type == null || type.equals(captured.type()))
                    && (channel == null || channel == captured.channel())) {
                result.add(captured);
            }
        }
        return result;
    }

    public void clear() {
        messages.clear();
    }
}
