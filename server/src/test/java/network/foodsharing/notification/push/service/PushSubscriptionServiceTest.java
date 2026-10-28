package network.foodsharing.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.push.domain.PushSubscription;
import network.foodsharing.notification.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** A23: upsert by endpoint, at most 10 subscriptions per user, delivery results. */
class PushSubscriptionServiceTest {

    private final PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
    private final PushSubscriptionService service = new PushSubscriptionService(repository,
            mock(HistoryService.class, Mockito.RETURNS_DEEP_STUBS), new AppProperties(),
            Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();
    private final PushSubscriptionService.NewSubscription browser =
            new PushSubscriptionService.NewSubscription("https://push.local/a", "key", "auth", "Firefox");

    @Test
    void theEleventhBrowserIsRejected() {
        when(repository.findByEndpointHash(any())).thenReturn(Optional.empty());
        when(repository.countByUserId(userId)).thenReturn(10L);
        assertThatThrownBy(() -> service.subscribe(userId, browser)).isInstanceOf(ApiException.class)
                .hasMessageContaining("TOO_MANY_PUSH_SUBSCRIPTIONS");
        verify(repository, never()).save(any());
    }

    @Test
    void aKnownEndpointIsUpdatedEvenAtTheLimit() {
        PushSubscription existing = new PushSubscription();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setFailureCount(3);
        when(repository.findByEndpointHash(PushSubscriptionService.hash(browser.endpoint())))
                .thenReturn(Optional.of(existing));
        when(repository.countByUserId(userId)).thenReturn(10L);
        when(repository.save(existing)).thenReturn(existing);

        service.subscribe(userId, browser);

        assertThat(existing.getFailureCount()).isZero();
        assertThat(existing.getUserAgent()).isEqualTo("Firefox");
    }

    @Test
    void goneSubscriptionsAreDeletedAndFailuresCounted() {
        PushSubscription row = new PushSubscription();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(row));
        service.recordResult(id, PushClient.Result.FAILED);
        assertThat(row.getFailureCount()).isEqualTo(1);
        service.recordResult(id, PushClient.Result.OK);
        assertThat(row.getFailureCount()).isZero();
        assertThat(row.getLastSuccessAt()).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
        service.recordResult(id, PushClient.Result.GONE);
        verify(repository).delete(row);
    }
}
