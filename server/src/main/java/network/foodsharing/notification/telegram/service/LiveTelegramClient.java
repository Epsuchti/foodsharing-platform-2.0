package network.foodsharing.notification.telegram.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code app.telegram.mode=live}: {@code https://api.telegram.org/bot<token>/<method>} with 10 s timeouts, every
 * call through the TELEGRAM breaker (A26); the probe is {@code getMe} (called directly, not through the breaker).
 */
@Component
@ConditionalOnProperty(name = "app.telegram.mode", havingValue = "live", matchIfMissing = true)
public class LiveTelegramClient implements TelegramClient {

    private final AppProperties.Telegram settings;
    private final IntegrationRegistry integrations;
    private final ObjectMapper objectMapper;
    private final RestClient http;

    public LiveTelegramClient(AppProperties properties, IntegrationRegistry integrations, ObjectMapper objectMapper) {
        this.settings = properties.getTelegram();
        this.integrations = integrations;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(settings.getTimeoutSeconds()));
        // getUpdates long-polls for up to 5 s, so the read timeout leaves room for it.
        factory.setReadTimeout(Duration.ofSeconds(settings.getTimeoutSeconds() + 5L));
        this.http = RestClient.builder().requestFactory(factory).build();
        integrations.registerProbe(Integration.TELEGRAM, this::probe);
    }

    private boolean configured() {
        return settings.getBotToken() != null && !settings.getBotToken().isBlank();
    }

    @Override
    public long sendMessage(String chatId, String text, Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("disable_web_page_preview", true);
        JsonNode result = integrations.call(Integration.TELEGRAM, "telegram-send", () -> post("sendMessage", body));
        return result.path("message_id").asLong();
    }

    @Override
    public List<Update> getUpdates(long offset) {
        Map<String, Object> body = Map.of("offset", offset, "timeout", 5, "allowed_updates", List.of("message"));
        JsonNode result = integrations.call(Integration.TELEGRAM, "telegram-poll", () -> post("getUpdates", body));
        List<Update> updates = new ArrayList<>();
        for (JsonNode node : result) {
            JsonNode message = node.has("message") ? node.path("message") : node.path("channel_post");
            JsonNode chat = message.path("chat");
            updates.add(new Update(node.path("update_id").asLong(), chat.path("id").asString(null),
                    chat.path("type").asString(null),
                    chat.hasNonNull("title") ? chat.path("title").asString() : null,
                    message.hasNonNull("text") ? message.path("text").asString() : null));
        }
        return updates;
    }

    @Override
    public void deleteMessage(String chatId, long messageId) {
        integrations.call(Integration.TELEGRAM, "telegram-delete",
                () -> post("deleteMessage", Map.of("chat_id", chatId, "message_id", messageId)));
    }

    @Override
    public boolean getMe() {
        return integrations.call(Integration.TELEGRAM, "telegram-getMe", () -> post("getMe", Map.of())).has("id");
    }

    private boolean probe() {
        try {
            return configured() && post("getMe", Map.of()).has("id");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private JsonNode post(String method, Map<String, Object> body) {
        if (!configured()) {
            throw new IllegalStateException("app.telegram.bot-token is not configured");
        }
        String response = http.post()
                .uri(settings.getApiUrl() + "/bot" + settings.getBotToken() + "/" + method)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);
        JsonNode json = objectMapper.readTree(response == null ? "{}" : response);
        if (!json.path("ok").asBoolean(false)) {
            throw new IllegalStateException("Telegram " + method + " failed: " + json.path("description").asString(""));
        }
        return json.path("result");
    }
}
