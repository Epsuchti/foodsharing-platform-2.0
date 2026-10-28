package network.foodsharing.notification.telegram.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import network.foodsharing.api.PublicTelegramApi;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.telegram.service.TelegramClient;
import network.foodsharing.notification.telegram.service.TelegramUpdateProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bot API webhook {@code POST /api/public/telegram/webhook/{secret}} (A28). Active only when
 * {@code app.telegram.webhook-secret} is set; a wrong secret answers 404. CSRF does not apply (SecurityConfig).
 */
@RestController
public class PublicTelegramWebhookController implements PublicTelegramApi {

    private final AppProperties properties;
    private final TelegramUpdateProcessor processor;

    public PublicTelegramWebhookController(AppProperties properties, TelegramUpdateProcessor processor) {
        this.properties = properties;
        this.processor = processor;
    }

    @Override
    public ResponseEntity<Void> receiveTelegramWebhook(String secret, Map<String, Object> body) {
        String expected = properties.getTelegram().getWebhookSecret();
        if (expected == null || expected.isBlank() || secret == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        TelegramClient.Update update = toUpdate(body);
        if (update != null) {
            processor.processAll(java.util.List.of(update));
        }
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    static TelegramClient.Update toUpdate(Map<String, Object> body) {
        if (!(body.get("update_id") instanceof Number updateId)) {
            return null;
        }
        Object raw = body.containsKey("message") ? body.get("message") : body.get("channel_post");
        if (!(raw instanceof Map<?, ?> message) || !(message.get("chat") instanceof Map<?, ?> chat)) {
            return new TelegramClient.Update(updateId.longValue(), null, null, null, null);
        }
        Object id = chat.get("id");
        return new TelegramClient.Update(updateId.longValue(), id == null ? null : String.valueOf(id),
                (String) ((Map<String, Object>) chat).get("type"), (String) ((Map<String, Object>) chat).get("title"),
                message.get("text") instanceof String text ? text : null);
    }
}
