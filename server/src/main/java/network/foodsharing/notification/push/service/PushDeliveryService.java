package network.foodsharing.notification.push.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.push.domain.PushSubscription;
import network.foodsharing.user.domain.User;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds and sends web push messages (A23). The title is the bell title {@code bell.<TYPE>} in the recipient's
 * language (server copy {@code i18n/bells/messages_<lang>.properties}, synced from the client catalog by
 * {@code scripts/sync-bell-messages.mjs}) with {@code {param}} placeholders filled; payload
 * {@code {title, body, link, icon, tag}} with tag = bell identifier (the service worker replaces older messages).
 */
@Service
public class PushDeliveryService {

    private final PushSubscriptionService subscriptions;
    private final PushClient client;
    private final MessageSource messages;
    private final ObjectMapper objectMapper;
    private final HistoryService history;

    public PushDeliveryService(PushSubscriptionService subscriptions, PushClient client, MessageSource messages,
                               ObjectMapper objectMapper, HistoryService history) {
        this.subscriptions = subscriptions;
        this.client = client;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.history = history;
    }

    /** Sends to every subscription of the user; @return whether at least one push was accepted. */
    @Transactional
    @NoHistory(reason = "part of a notification that is recorded as NOTIFICATION_SENT")
    public boolean send(User recipient, NotificationType type, String titleKey, Map<String, String> params,
                        String link, String icon, String tag) {
        PushClient.PushMessage message = message(recipient, type, titleKey, params, link, icon, tag);
        boolean delivered = false;
        for (PushSubscription subscription : subscriptions.subscriptions(recipient.getId())) {
            PushClient.Result result;
            try {
                result = client.send(subscription, message);
            } catch (RuntimeException ex) {
                result = PushClient.Result.FAILED;
            }
            subscriptions.recordResult(subscription.getId(), result);
            delivered |= result == PushClient.Result.OK;
        }
        return delivered;
    }

    /**
     * {@code POST /api/push/test}: a GENERIC push to all my subscriptions.
     *
     * @throws ApiException 409 NO_PUSH_SUBSCRIPTION
     */
    @Transactional
    public void sendTest(User user) {
        if (!subscriptions.hasSubscription(user.getId())) {
            throw ApiException.conflict(ApiErrorCode.NO_PUSH_SUBSCRIPTION);
        }
        send(user, NotificationType.GENERIC, NotificationType.GENERIC.defaultTitleKey(), Map.of(), "/notifications", "bell",
                "PUSH_TEST-" + user.getId());
        history.record(history.event(NotificationAction.PUSH_SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(user.getId()).metadata(Map.of("test", true)).build());
    }

    PushClient.PushMessage message(User recipient, NotificationType type, String titleKey, Map<String, String> params,
                                   String link, String icon, String tag) {
        Locale locale = Locale.forLanguageTag(recipient.getPreferredLanguage() == null ? "de"
                : recipient.getPreferredLanguage().getValue());
        String title = interpolate(messages.getMessage(titleKey, null,
                messages.getMessage(type.defaultTitleKey(), null, type.name(), locale), locale), params);
        String body = body(params);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("link", link);
        payload.put("icon", icon);
        payload.put("tag", tag == null ? type.name() + "-" + UUID.randomUUID() : tag);
        return new PushClient.PushMessage(type.name(), title, body, link, icon, (String) payload.get("tag"), params,
                objectMapper.writeValueAsString(payload));
    }

    private static String body(Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        for (String key : new String[] {"storeName", "when", "regionName", "userName", "message"}) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                if (!body.isEmpty()) {
                    body.append(" · ");
                }
                body.append(value);
            }
        }
        return body.toString();
    }

    static String interpolate(String text, Map<String, String> params) {
        String result = text;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
