package network.foodsharing.notification.telegram.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Telegram Bot API (A28): {@link LiveTelegramClient} ({@code app.telegram.mode=live}, through the TELEGRAM breaker)
 * or {@link LogTelegramClient} (local/e2e/test: captured for {@code GET /api/dev/notifications?channel=TELEGRAM}).
 */
public interface TelegramClient {

    /** One incoming update (message text plus its chat). {@code chatType}: private, group, supergroup, channel. */
    record Update(long updateId, String chatId, String chatType, String chatTitle, String text) {
    }

    /** What the log client captures besides chat and text; all fields optional. */
    record Context(UUID userId, String type, Map<String, String> params, String link, String identifier,
                   String title) {

        public static Context none() {
            return new Context(null, null, Map.of(), null, null, null);
        }
    }

    /** Sends a plain-text message; @return the Telegram message id. */
    long sendMessage(String chatId, String text, Context context);

    /** Updates after {@code offset} (long polling with a short timeout). */
    List<Update> getUpdates(long offset);

    /** Removes a message the bot sent (e.g. an advert that became obsolete). */
    void deleteMessage(String chatId, long messageId);

    /** Whether the bot token is valid (Bot API {@code getMe}); the TELEGRAM probe. */
    boolean getMe();
}
