package network.foodsharing.notification.telegram.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Group chats the bot has seen in updates during the last 30 days (in memory, at most 500), offered when an
 * ambassador or store manager picks an alert chat ({@code GET /api/telegram/chats}). Configured chats stay
 * selectable through their stored id and title, so losing this list on restart only hides new candidates.
 */
@Component
public class TelegramSeenChats {

    private static final Duration KEEP = Duration.ofDays(30);
    private static final int MAX = 500;

    /** A chat seen in an update; {@code type} is the Bot API chat type (group, supergroup, channel). */
    public record SeenChat(String chatId, String title, String type, Instant seenAt) {
    }

    private final Map<String, SeenChat> chats = new ConcurrentHashMap<>();
    private final Clock clock;

    public TelegramSeenChats(Clock clock) {
        this.clock = clock;
    }

    public void seen(String chatId, String title, String type) {
        if (chatId == null || "private".equals(type)) {
            return;
        }
        chats.put(chatId, new SeenChat(chatId, title == null ? chatId : title, type == null ? "group" : type,
                clock.instant()));
        if (chats.size() > MAX) {
            chats.values().stream().min(Comparator.comparing(SeenChat::seenAt))
                    .ifPresent(oldest -> chats.remove(oldest.chatId()));
        }
    }

    /** Newest first. */
    public List<SeenChat> recent() {
        Instant since = clock.instant().minus(KEEP);
        return chats.values().stream().filter(chat -> chat.seenAt().isAfter(since))
                .sorted(Comparator.comparing(SeenChat::seenAt).reversed()).toList();
    }

    public void clear() {
        chats.clear();
    }
}
