package network.foodsharing.notification.service;

import java.time.Duration;
import java.time.Instant;
import network.foodsharing.common.jobs.CleanupTask;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.push.repository.PushSubscriptionRepository;
import network.foodsharing.notification.repository.BellRepository;
import network.foodsharing.notification.telegram.service.TelegramLinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification part of the {@code cleanup} job (§6.3): push subscriptions with {@code failure_count ≥
 * app.push.max-failures}, expired Telegram link tokens, bells read more than 90 days or deleted more than 30 days
 * ago, SENT mails older than 90 days.
 */
@Service
public class NotificationCleanup implements CleanupTask {

    static final Duration BELL_READ_RETENTION = Duration.ofDays(90);
    static final Duration BELL_DELETED_RETENTION = Duration.ofDays(30);
    static final Duration MAIL_SENT_RETENTION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanup.class);

    private final PushSubscriptionRepository subscriptions;
    private final BellRepository bells;
    private final TelegramLinkService telegram;
    private final MailOutboxService mails;
    private final AppProperties properties;

    public NotificationCleanup(PushSubscriptionRepository subscriptions, BellRepository bells,
                               TelegramLinkService telegram, MailOutboxService mails, AppProperties properties) {
        this.subscriptions = subscriptions;
        this.bells = bells;
        this.telegram = telegram;
        this.mails = mails;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "notifications";
    }

    @Override
    @Transactional
    @NoHistory(reason = "retention housekeeping (cleanup job)")
    public int cleanup(Instant now) {
        int push = subscriptions.deleteFailing(properties.getPush().getMaxFailures());
        int tokens = telegram.deleteExpiredTokens();
        int bellCount = bells.purge(now.minus(BELL_READ_RETENTION), now.minus(BELL_DELETED_RETENTION));
        int mailCount = mails.purgeSentBefore(now.minus(MAIL_SENT_RETENTION));
        log.info("cleanup: {} push subscriptions, {} Telegram tokens, {} bells, {} mails", push, tokens, bellCount,
                mailCount);
        return push + tokens + bellCount + mailCount;
    }
}
