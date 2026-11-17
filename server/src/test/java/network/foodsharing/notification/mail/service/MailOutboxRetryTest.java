package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import org.junit.jupiter.api.Test;

/** N6/B3: ORGA puts a failed mail back into the queue now with fresh attempts; unknown and sent mails are refused. */
class MailOutboxRetryTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final MailOutboxRepository repository = mock(MailOutboxRepository.class);
    private final MailOutboxService service = new MailOutboxService(repository, mock(MailTemplateService.class),
            mock(MailDeliveryService.class), mock(HistoryService.class, RETURNS_DEEP_STUBS), new AppProperties(),
            Clock.fixed(NOW, ZoneOffset.UTC), mock(MailRateLimiter.class));

    private MailOutboxMessage mail(MailStatus status) {
        MailOutboxMessage message = new MailOutboxMessage();
        message.setId(UUID.randomUUID());
        message.setStatus(status);
        message.setAttempts(8);
        message.setTemplate("pickup/removed");
        message.setLastError("boom");
        message.setNextAttemptAt(NOW.minusSeconds(3600));
        message.setRateLimitedUntil(NOW.plusSeconds(600));
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        return message;
    }

    @Test
    void aFailedMailIsQueuedAgainNowWithFreshAttempts() {
        MailOutboxMessage failed = mail(MailStatus.FAILED);
        MailOutboxMessage retried = service.retry(failed.getId());
        assertThat(retried.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(retried.getAttempts()).isZero();
        assertThat(retried.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(retried.getRateLimitedUntil()).isNull();
    }

    @Test
    void unknownAndSentMailsAreRefused() {
        assertThatThrownBy(() -> service.retry(UUID.randomUUID())).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.MAIL_NOT_FOUND));
        MailOutboxMessage sent = mail(MailStatus.SENT);
        assertThatThrownBy(() -> service.retry(sent.getId())).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.MAIL_NOT_RETRYABLE));
        assertThat(sent.getStatus()).isEqualTo(MailStatus.SENT);
    }
}
