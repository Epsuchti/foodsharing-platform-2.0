package network.foodsharing.notification.telegram.service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import network.foodsharing.history.NoHistory;
import network.foodsharing.notification.telegram.repository.TelegramOffsetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles incoming Bot API updates from the poll job, the webhook or {@code POST /api/dev/telegram/incoming} (A28).
 * Idempotent by {@code update_id}: updates at or below the stored {@code telegram_offset} are ignored. Group chats are
 * remembered in {@link TelegramSeenChats}; private chats understand {@code /start <token>} and {@code /stop}.
 */
@Service
public class TelegramUpdateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateProcessor.class);

    private final TelegramOffsetRepository offsets;
    private final TelegramLinkService links;
    private final TelegramSeenChats seenChats;
    private final Clock clock;

    public TelegramUpdateProcessor(TelegramOffsetRepository offsets, TelegramLinkService links,
                                   TelegramSeenChats seenChats, Clock clock) {
        this.offsets = offsets;
        this.links = links;
        this.seenChats = seenChats;
        this.clock = clock;
    }

    /** Last processed update id (0 = none). */
    @Transactional(readOnly = true)
    public long lastUpdateId() {
        return offsets.lastUpdateId().orElse(0L);
    }

    /** Processes a batch in order; @return how many were new. */
    public int processAll(List<TelegramClient.Update> updates) {
        int processed = 0;
        for (TelegramClient.Update update : updates) {
            try {
                if (process(update)) {
                    processed++;
                }
            } catch (RuntimeException ex) {
                log.warn("Telegram update {} failed: {}", update.updateId(), ex.toString());
            }
        }
        return processed;
    }

    /**
     * Processes one update unless it was seen before; the offset moves in the same transaction.
     *
     * @return false for an already processed update
     */
    @Transactional
    @NoHistory(reason = "moves the offset only; /start and /stop record NOTIFICATION_TELEGRAM_LINKED / UNLINKED")
    public boolean process(TelegramClient.Update update) {
        long last = offsets.lastUpdateId().orElse(0L);
        if (update.updateId() <= last) {
            return false;
        }
        offsets.save(update.updateId(), clock.instant());
        if (update.chatId() == null) {
            return true;
        }
        boolean privateChat = update.chatType() == null || "private".equals(update.chatType());
        if (!privateChat) {
            seenChats.seen(update.chatId(), update.chatTitle(), update.chatType());
            return true;
        }
        String text = update.text() == null ? "" : update.text().strip();
        String command = command(text);
        switch (command) {
            case "/start" -> {
                String argument = text.contains(" ") ? text.substring(text.indexOf(' ') + 1).strip() : "";
                if (argument.isEmpty()) {
                    links.help(update.chatId());
                } else {
                    links.start(update.chatId(), argument);
                }
            }
            case "/stop" -> links.stop(update.chatId());
            default -> links.help(update.chatId());
        }
        return true;
    }

    /** Dev endpoint: the next synthetic update id (after the stored offset). */
    @Transactional(readOnly = true)
    public long nextSyntheticUpdateId() {
        return lastUpdateId() + 1;
    }

    /** {@code /start@foodsharing_bot abc} → {@code /start}. */
    static String command(String text) {
        if (!text.startsWith("/")) {
            return "";
        }
        String first = text.split("\\s+", 2)[0];
        int at = first.indexOf('@');
        return (at > 0 ? first.substring(0, at) : first).toLowerCase(Locale.ROOT);
    }
}
