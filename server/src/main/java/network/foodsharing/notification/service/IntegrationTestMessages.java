package network.foodsharing.notification.service;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.notification.mail.service.MailDeliveryService;
import network.foodsharing.notification.push.service.PushDeliveryService;
import network.foodsharing.notification.telegram.service.TelegramDeliveryService;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Component;

/**
 * Test messages for the admin integrations page ({@code POST /api/admin/integrations/{integration}/test}, A26):
 * SMTP → a mail straight through the transport to the caller, TELEGRAM → the given chat, WEB_PUSH → the caller's
 * subscriptions. Every send goes through the integration's breaker.
 */
@Component
public class IntegrationTestMessages {

    private final MailDeliveryService mail;
    private final PushDeliveryService push;
    private final TelegramDeliveryService telegram;

    public IntegrationTestMessages(MailDeliveryService mail, PushDeliveryService push,
                                   TelegramDeliveryService telegram) {
        this.mail = mail;
        this.push = push;
        this.telegram = telegram;
    }

    /**
     * @throws ApiException 400 VALIDATION_FAILED (integration without test message, TELEGRAM without target),
     *                      409 NO_PUSH_SUBSCRIPTION / TELEGRAM_NOT_CONFIGURED, 503 INTEGRATION_UNAVAILABLE
     */
    public void send(Integration integration, User caller, String target) {
        switch (integration) {
            case SMTP -> mail.sendTest(caller.getEmail());
            case WEB_PUSH -> push.sendTest(caller);
            case TELEGRAM -> {
                if (target == null || target.isBlank()) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
                }
                telegram.requireConfigured();
                telegram.sendText(target.strip(), telegram.text("telegram.test",
                        TelegramDeliveryService.locale(caller)), caller, "INTEGRATION_TEST");
            }
            default -> throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
    }
}
