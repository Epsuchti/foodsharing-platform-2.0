package network.foodsharing.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.push.repository.PushSubscriptionRepository;
import network.foodsharing.notification.repository.BellRepository;
import network.foodsharing.notification.telegram.service.TelegramLinkService;
import org.junit.jupiter.api.Test;

/**
 * §6.3 cleanup, notification part: failing push subscriptions, expired Telegram link tokens, bells read 90 days or
 * deleted 30 days ago and sent mails older than 90 days go; the counts add up to the task's result.
 */
class NotificationCleanupTest {

    @Test
    void purgesEveryKindWithItsRetention() {
        PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
        BellRepository bells = mock(BellRepository.class);
        TelegramLinkService telegram = mock(TelegramLinkService.class);
        MailOutboxService mails = mock(MailOutboxService.class);
        AppProperties properties = new AppProperties();
        Instant now = Instant.parse("2026-11-02T09:00:00Z");
        int maxFailures = properties.getPush().getMaxFailures();
        when(subscriptions.deleteFailing(maxFailures)).thenReturn(1);
        when(telegram.deleteExpiredTokens()).thenReturn(2);
        when(bells.purge(now.minus(Duration.ofDays(90)), now.minus(Duration.ofDays(30)))).thenReturn(3);
        when(mails.purgeSentBefore(now.minus(Duration.ofDays(90)))).thenReturn(4);

        int removed = new NotificationCleanup(subscriptions, bells, telegram, mails, properties).cleanup(now);

        assertThat(removed).isEqualTo(10);
        verify(telegram).deleteExpiredTokens();
    }
}
