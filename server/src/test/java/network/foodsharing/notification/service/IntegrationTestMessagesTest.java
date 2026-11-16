package network.foodsharing.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailDeliveryService;
import network.foodsharing.notification.push.service.PushClient;
import network.foodsharing.notification.push.service.PushDeliveryService;
import network.foodsharing.notification.push.service.PushSubscriptionService;
import network.foodsharing.notification.telegram.service.TelegramDeliveryService;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * A26 test messages of the integrations page: TELEGRAM needs a target chat and sends there; WEB_PUSH needs a push
 * subscription of the caller (409 NO_PUSH_SUBSCRIPTION, also for {@code POST /api/push/test}).
 */
class IntegrationTestMessagesTest {

    private final PushSubscriptionService subscriptions = mock(PushSubscriptionService.class);
    private final PushDeliveryService push = new PushDeliveryService(subscriptions, mock(PushClient.class),
            new StaticMessageSource(), JsonMapper.builder().build(), mock(HistoryService.class));
    private final TelegramDeliveryService telegram = mock(TelegramDeliveryService.class);
    private final IntegrationTestMessages messages = new IntegrationTestMessages(mock(MailDeliveryService.class), push,
            telegram);
    private final User caller = new User();

    {
        caller.setId(UUID.randomUUID());
    }

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    @Test
    void telegramNeedsATargetAndSendsThere() {
        assertThat(code(() -> messages.send(Integration.TELEGRAM, caller, " "))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> messages.send(Integration.TELEGRAM, caller, null))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(telegram, never()).sendText(anyString(), any(), any(), anyString());

        messages.send(Integration.TELEGRAM, caller, " -4242 ");
        verify(telegram).requireConfigured();
        verify(telegram).sendText(eq("-4242"), any(), eq(caller), eq("INTEGRATION_TEST"));
    }

    @Test
    void aPushTestNeedsASubscription() {
        when(subscriptions.hasSubscription(caller.getId())).thenReturn(false);
        assertThatThrownBy(() -> messages.send(Integration.WEB_PUSH, caller, null))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.NO_PUSH_SUBSCRIPTION));
    }

    @Test
    void integrationsWithoutTestMessageAreRefused() {
        assertThat(code(() -> messages.send(Integration.GEOCODING, caller, null))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }
}
