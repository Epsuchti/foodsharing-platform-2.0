package network.foodsharing.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.push.domain.PushSubscription;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import tools.jackson.databind.json.JsonMapper;

/** A23: localized title with params; 404/410 removes the subscription, other failures are counted. */
class PushDeliveryServiceTest {

    private final PushSubscriptionService subscriptions = mock(PushSubscriptionService.class);
    private final PushClient client = mock(PushClient.class);
    private final StaticMessageSource messages = new StaticMessageSource();
    private final PushDeliveryService service = new PushDeliveryService(subscriptions, client, messages,
            JsonMapper.builder().build(), mock(HistoryService.class));

    @Test
    void goneSubscriptionsAreRemovedAndFailuresCounted() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPreferredLanguage(Language.FR);
        messages.addMessage("bell.PICKUP_CONFIRMED", java.util.Locale.forLanguageTag("fr"),
                "Collecte chez {storeName} confirmée");
        PushSubscription gone = subscription(user);
        PushSubscription failing = subscription(user);
        when(subscriptions.subscriptions(user.getId())).thenReturn(List.of(gone, failing));
        when(client.send(any(), any())).thenReturn(PushClient.Result.GONE, PushClient.Result.FAILED);

        boolean delivered = service.send(user, NotificationType.PICKUP_CONFIRMED, "bell.PICKUP_CONFIRMED",
                Map.of("storeName", "Coop"), "/stores/1", "calendar", "PICKUP_CONFIRMED-1");

        assertThat(delivered).isFalse();
        verify(subscriptions).recordResult(gone.getId(), PushClient.Result.GONE);
        verify(subscriptions).recordResult(failing.getId(), PushClient.Result.FAILED);
        PushClient.PushMessage message = service.message(user, NotificationType.PICKUP_CONFIRMED,
                "bell.PICKUP_CONFIRMED", Map.of("storeName", "Coop"), "/stores/1", "calendar", "tag-1");
        assertThat(message.title()).isEqualTo("Collecte chez Coop confirmée");
        assertThat(message.json()).contains("\"tag\":\"tag-1\"").contains("\"link\":\"/stores/1\"");
    }

    private static PushSubscription subscription(User user) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(user.getId());
        subscription.setEndpoint("https://push.local/" + subscription.getId());
        return subscription;
    }
}
