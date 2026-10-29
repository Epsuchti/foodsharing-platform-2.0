package network.foodsharing.notification.telegram.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.notification.service.StoreAlertAccess;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat.OwnerType;
import network.foodsharing.notification.telegram.repository.TelegramAlertChatRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region and store alert chats (A28): region → mayAdministrateRegion, store → mayUseAutomations. The list of
 * selectable chats ({@link #seenChats}) is open to ORGA, ambassadors and store managers.
 */
@Service
public class TelegramChatService {

    /** One configured chat. */
    public record ChatConfig(String chatId, String title, boolean enabled) {
    }

    private final TelegramAlertChatRepository chats;
    private final TelegramSeenChats seenChats;
    private final TelegramDeliveryService delivery;
    private final PermissionService permissions;
    private final StoreAlertAccess storeAccess;
    private final HistoryService history;
    private final ObjectProvider<RegionSenderNames> senderNames;

    public TelegramChatService(TelegramAlertChatRepository chats, TelegramSeenChats seenChats,
                               TelegramDeliveryService delivery, PermissionService permissions,
                               StoreAlertAccess storeAccess, HistoryService history,
                               ObjectProvider<RegionSenderNames> senderNames) {
        this.senderNames = senderNames;
        this.chats = chats;
        this.seenChats = seenChats;
        this.delivery = delivery;
        this.permissions = permissions;
        this.storeAccess = storeAccess;
        this.history = history;
    }

    /** @throws ApiException 403 unless ORGA, ambassador somewhere or role ≥ STORE_MANAGER */
    public List<TelegramSeenChats.SeenChat> seenChats(SessionPrincipal actor) {
        if (!permissions.isOrga(actor) && !permissions.isAmbassadorOfAny(actor)
                && !PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return seenChats.recent();
    }

    @Transactional(readOnly = true)
    public List<ChatConfig> list(SessionPrincipal actor, OwnerType ownerType, UUID ownerId) {
        requireAccess(actor, ownerType, ownerId);
        return chats.findByOwnerTypeAndOwnerIdOrderByTitleAsc(ownerType, ownerId).stream()
                .map(c -> new ChatConfig(c.getChatId(), c.getTitle(), c.isEnabled())).toList();
    }

    /** Replaces the owner's chats; records TELEGRAM_CHAT_ADDED / TELEGRAM_CHAT_REMOVED per change. */
    @Transactional
    public List<ChatConfig> replace(SessionPrincipal actor, OwnerType ownerType, UUID ownerId, List<ChatConfig> wanted) {
        requireAccess(actor, ownerType, ownerId);
        Map<String, TelegramAlertChat> existing = new HashMap<>();
        for (TelegramAlertChat chat : chats.findByOwnerTypeAndOwnerIdOrderByTitleAsc(ownerType, ownerId)) {
            existing.put(chat.getChatId(), chat);
        }
        Map<String, ChatConfig> target = new LinkedHashMap<>();
        for (ChatConfig config : wanted) {
            if (config.chatId() == null || config.chatId().isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            target.put(config.chatId().strip(), config);
        }
        for (TelegramAlertChat chat : new ArrayList<>(existing.values())) {
            if (!target.containsKey(chat.getChatId())) {
                chats.delete(chat);
                record(NotificationAction.TELEGRAM_CHAT_REMOVED, ownerType, ownerId, chat.getChatId());
            }
        }
        for (Map.Entry<String, ChatConfig> entry : target.entrySet()) {
            TelegramAlertChat chat = existing.get(entry.getKey());
            if (chat == null) {
                chat = new TelegramAlertChat();
                chat.setOwnerType(ownerType);
                chat.setOwnerId(ownerId);
                chat.setChatId(entry.getKey());
                record(NotificationAction.TELEGRAM_CHAT_ADDED, ownerType, ownerId, entry.getKey());
            }
            String title = entry.getValue().title();
            chat.setTitle(title == null || title.isBlank() ? entry.getKey() : title.strip());
            chat.setEnabled(entry.getValue().enabled());
            chats.save(chat);
        }
        chats.flush();
        return chats.findByOwnerTypeAndOwnerIdOrderByTitleAsc(ownerType, ownerId).stream()
                .map(c -> new ChatConfig(c.getChatId(), c.getTitle(), c.isEnabled())).toList();
    }

    /**
     * Sends a test message to one configured chat of the owner.
     *
     * @throws ApiException 404 RESOURCE_NOT_FOUND when the chat is not configured, 409 TELEGRAM_NOT_CONFIGURED
     */
    @Transactional
    public void sendTest(SessionPrincipal actor, OwnerType ownerType, UUID ownerId, String chatId) {
        requireAccess(actor, ownerType, ownerId);
        delivery.requireConfigured();
        TelegramAlertChat chat = chats.findByOwnerTypeAndOwnerIdOrderByTitleAsc(ownerType, ownerId).stream()
                .filter(c -> c.getChatId().equals(chatId)).findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
        delivery.sendText(chat.getChatId(), delivery.text("telegram.chat-test", Locale.GERMAN, chat.getTitle()), null,
                "TELEGRAM_CHAT_TEST");
        history.record(history.event(NotificationAction.TELEGRAM_SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("ownerType", ownerType.name(), "ownerId", ownerId.toString(), "chatId",
                        chat.getChatId(), "test", true)).build());
    }

    /** Sends a text to every enabled chat of the owner (alerts, adverts); @return how many chats got it. */
    public int sendToOwner(OwnerType ownerType, UUID ownerId, String text, String type) {
        int sent = 0;
        for (TelegramAlertChat chat : chats.findByOwnerTypeAndOwnerIdAndEnabledTrue(ownerType, ownerId)) {
            delivery.sendText(chat.getChatId(), text, null, type);
            sent++;
        }
        return sent;
    }

    /**
     * Sends a text to one chat of the owner (open-slot adverts, sign-out alerts). The chat must be configured and
     * enabled for the owner; {@code chatIds} empty = every enabled chat of the owner.
     *
     * @return the sent messages (chat id, message id)
     */
    public List<SentMessage> sendToChats(OwnerType ownerType, UUID ownerId, java.util.Collection<String> chatIds,
                                         String text, String type) {
        List<SentMessage> sent = new java.util.ArrayList<>();
        for (TelegramAlertChat chat : chats.findByOwnerTypeAndOwnerIdAndEnabledTrue(ownerType, ownerId)) {
            if (chatIds.isEmpty() || chatIds.contains(chat.getChatId())) {
                sent.add(new SentMessage(chat.getChatId(), delivery.sendText(chat.getChatId(), text, null, type)));
            }
        }
        return sent;
    }

    /**
     * Automation messages of a store (adverts, sign-out alerts): to the store's enabled chats with these ids, and to
     * the region's enabled chats for ids the store does not have ({@code chatIds} empty = every enabled store chat).
     * The message ends with the signature of the store's region (AU11 region system sender).
     */
    public List<SentMessage> sendForStore(UUID storeId, UUID regionId, java.util.Collection<String> chatIds,
                                          String unsigned, String type) {
        String text = RegionSenderNames.signed(unsigned,
                RegionSenderNames.resolve(senderNames, regionId).orElse(null));
        List<SentMessage> sent = new java.util.ArrayList<>(sendToChats(OwnerType.STORE, storeId, chatIds, text,
                type));
        if (!chatIds.isEmpty() && regionId != null) {
            java.util.Set<String> missing = new java.util.HashSet<>(chatIds);
            sent.forEach(m -> missing.remove(m.chatId()));
            if (!missing.isEmpty()) {
                sent.addAll(sendToChats(OwnerType.REGION, regionId, missing, text, type));
            }
        }
        return sent;
    }

    /** Deletes an earlier message; failures are only logged by the client breaker. */
    public void deleteMessage(String chatId, long messageId) {
        delivery.deleteText(chatId, messageId);
    }

    /** One message sent to a chat. */
    public record SentMessage(String chatId, long messageId) {
    }

    private void requireAccess(SessionPrincipal actor, OwnerType ownerType, UUID ownerId) {
        if (ownerType == OwnerType.REGION) {
            permissions.requireMayAdministrateRegion(actor, ownerId);
        } else if (!storeAccess.mayUseAutomations(actor, ownerId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private void record(NotificationAction action, OwnerType ownerType, UUID ownerId, String chatId) {
        history.record(history.event(action, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("ownerType", ownerType.name(), "ownerId", ownerId.toString(), "chatId", chatId))
                .build());
    }
}
