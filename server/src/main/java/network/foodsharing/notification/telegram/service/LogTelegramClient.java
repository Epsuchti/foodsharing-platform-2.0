package network.foodsharing.notification.telegram.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.service.CapturedMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code app.telegram.mode=log} (local/e2e/test): messages are logged and captured with chat id, text and a fake
 * message id, never sent. Incoming updates come from {@code POST /api/dev/telegram/incoming} instead of polling.
 */
@Component
@ConditionalOnProperty(name = "app.telegram.mode", havingValue = "log")
public class LogTelegramClient implements TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(LogTelegramClient.class);

    private final CapturedMessages captured;
    private final AtomicLong messageIds = new AtomicLong(1000);

    public LogTelegramClient(CapturedMessages captured) {
        this.captured = captured;
    }

    @Override
    public long sendMessage(String chatId, String text, Context context) {
        Context c = context == null ? Context.none() : context;
        captured.add(c.userId(), c.type() == null ? "TELEGRAM" : c.type(), NotificationChannelType.TELEGRAM, c.params(),
                c.link(), c.identifier(), c.title(), text, chatId);
        long id = messageIds.incrementAndGet();
        log.info("Telegram (log mode) to {} (message {}): {}", chatId, id, text);
        return id;
    }

    @Override
    public List<Update> getUpdates(long offset) {
        return List.of();
    }

    @Override
    public void deleteMessage(String chatId, long messageId) {
        log.info("Telegram (log mode) delete message {} in {}", messageId, chatId);
    }

    @Override
    public boolean getMe() {
        return true;
    }
}
