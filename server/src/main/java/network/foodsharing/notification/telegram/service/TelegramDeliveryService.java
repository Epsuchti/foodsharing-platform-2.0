package network.foodsharing.notification.telegram.service;

import java.util.Locale;
import java.util.Map;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.user.domain.User;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds and sends Telegram messages to users (A23/A28): the bell title {@code bell.<TYPE>} in the recipient language,
 * the main parameters and the absolute link, as plain text.
 */
@Service
public class TelegramDeliveryService {

    private static final String[] BODY_PARAMS = {"storeName", "when", "regionName", "userName", "message"};

    private final TelegramClient client;
    private final MessageSource messages;
    private final AppProperties properties;
    private final IntegrationRegistry integrations;
    private final HistoryService history;

    public TelegramDeliveryService(TelegramClient client, MessageSource messages, AppProperties properties,
                                   IntegrationRegistry integrations, HistoryService history) {
        this.client = client;
        this.messages = messages;
        this.properties = properties;
        this.integrations = integrations;
        this.history = history;
    }

    /** Whether the bot can send at all (log mode, or live with a token). */
    public boolean configured() {
        return integrations.mode(Integration.TELEGRAM) == IntegrationRegistry.Mode.LOG
                || (properties.getTelegram().getBotToken() != null && !properties.getTelegram().getBotToken().isBlank());
    }

    /** Sends one notification to the user's linked chat; @return false when not linked. */
    public boolean send(User recipient, NotificationType type, String titleKey, Map<String, String> params,
                        String link, String identifier) {
        if (recipient.getTelegramChatId() == null) {
            return false;
        }
        Locale locale = locale(recipient);
        String title = interpolate(messages.getMessage(titleKey == null ? type.defaultTitleKey() : titleKey, null,
                messages.getMessage(type.defaultTitleKey(), null, type.name(), locale), locale), params);
        StringBuilder text = new StringBuilder(title);
        StringBuilder body = new StringBuilder();
        for (String key : BODY_PARAMS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                body.append(body.isEmpty() ? "" : " · ").append(value);
            }
        }
        if (!body.isEmpty()) {
            text.append('\n').append(body);
        }
        if (link != null) {
            text.append('\n').append(absolute(link));
        }
        String signed = RegionSenderNames.signed(text.toString(), params.get(RegionSenderNames.MAIL_VARIABLE));
        client.sendMessage(recipient.getTelegramChatId(), signed, new TelegramClient.Context(
                recipient.getId(), type.name(), params, link, identifier, title));
        return true;
    }

    /**
     * {@code POST /api/telegram/test}: a test message to my linked chat.
     *
     * @throws ApiException 409 TELEGRAM_NOT_CONFIGURED / TELEGRAM_NOT_LINKED
     */
    @Transactional
    public void sendTest(User user) {
        requireConfigured();
        if (user.getTelegramChatId() == null) {
            throw ApiException.conflict(ApiErrorCode.TELEGRAM_NOT_LINKED);
        }
        sendText(user.getTelegramChatId(), text("telegram.test", locale(user)), user, "TELEGRAM_TEST");
        history.record(history.event(NotificationAction.TELEGRAM_SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(user.getId()).metadata(Map.of("test", true)).build());
    }

    /** A plain text message to any chat (link replies, alert chats). */
    public long sendText(String chatId, String text, User user, String type) {
        return client.sendMessage(chatId, text, new TelegramClient.Context(user == null ? null : user.getId(),
                type, Map.of(), null, null, null));
    }

    /** Deletes a message sent earlier (open-slot adverts that are no longer needed). */
    public void deleteText(String chatId, long messageId) {
        client.deleteMessage(chatId, messageId);
    }

    public void requireConfigured() {
        if (!configured()) {
            throw ApiException.conflict(ApiErrorCode.TELEGRAM_NOT_CONFIGURED);
        }
    }

    public String text(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args.length == 0 ? null : args, key, locale);
    }

    public static Locale locale(User user) {
        return Locale.forLanguageTag(user == null || user.getPreferredLanguage() == null ? "de"
                : user.getPreferredLanguage().getValue());
    }

    private String absolute(String link) {
        return link.startsWith("http") ? link : properties.getBaseUrl() + link;
    }

    static String interpolate(String text, Map<String, String> params) {
        String result = text;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
