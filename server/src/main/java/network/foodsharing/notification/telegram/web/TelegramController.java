package network.foodsharing.notification.telegram.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.TelegramApi;
import network.foodsharing.api.model.TelegramChat;
import network.foodsharing.api.model.TelegramChatConfig;
import network.foodsharing.api.model.TelegramChatConfigList;
import network.foodsharing.api.model.TelegramChatTest;
import network.foodsharing.api.model.TelegramChatType;
import network.foodsharing.api.model.TelegramLinkToken;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat.OwnerType;
import network.foodsharing.notification.telegram.service.TelegramChatService;
import network.foodsharing.notification.telegram.service.TelegramDeliveryService;
import network.foodsharing.notification.telegram.service.TelegramLinkService;
import network.foodsharing.notification.telegram.service.TelegramSeenChats;
import network.foodsharing.security.CurrentActorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Telegram linking and region/store alert chats (A28). */
@RestController
public class TelegramController implements TelegramApi {

    private final TelegramLinkService links;
    private final TelegramDeliveryService delivery;
    private final TelegramChatService chats;
    private final CurrentActorService currentActor;

    public TelegramController(TelegramLinkService links, TelegramDeliveryService delivery, TelegramChatService chats,
                              CurrentActorService currentActor) {
        this.links = links;
        this.delivery = delivery;
        this.chats = chats;
        this.currentActor = currentActor;
    }

    @Override
    public ResponseEntity<TelegramLinkToken> createTelegramLinkToken() {
        TelegramLinkService.LinkToken token = links.createToken(currentActor.requireUser());
        return ResponseEntity.ok(new TelegramLinkToken().token(token.token()).deepLink(token.deepLink())
                .expiresAt(TimeMapper.toOffset(token.expiresAt())));
    }

    @Override
    public ResponseEntity<Void> unlinkTelegram() {
        links.unlink(currentActor.requireUser());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> sendTelegramTest() {
        delivery.sendTest(currentActor.requireUser());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<TelegramChat>> listTelegramChats() {
        return ResponseEntity.ok(chats.seenChats(currentActor.requirePrincipal()).stream().map(this::toApi).toList());
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> getRegionTelegramChats(UUID regionId) {
        return ResponseEntity.ok(toApi(chats.list(currentActor.requirePrincipal(), OwnerType.REGION, regionId)));
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> putRegionTelegramChats(UUID regionId, TelegramChatConfigList body) {
        return ResponseEntity.ok(toApi(chats.replace(currentActor.requirePrincipal(), OwnerType.REGION, regionId,
                fromApi(body))));
    }

    @Override
    public ResponseEntity<Void> testRegionTelegramChat(UUID regionId, TelegramChatTest body) {
        chats.sendTest(currentActor.requirePrincipal(), OwnerType.REGION, regionId, body.getChatId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> getStoreTelegramChats(UUID storeId) {
        return ResponseEntity.ok(toApi(chats.list(currentActor.requirePrincipal(), OwnerType.STORE, storeId)));
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> putStoreTelegramChats(UUID storeId, TelegramChatConfigList body) {
        return ResponseEntity.ok(toApi(chats.replace(currentActor.requirePrincipal(), OwnerType.STORE, storeId,
                fromApi(body))));
    }

    @Override
    public ResponseEntity<Void> testStoreTelegramChat(UUID storeId, TelegramChatTest body) {
        chats.sendTest(currentActor.requirePrincipal(), OwnerType.STORE, storeId, body.getChatId());
        return ResponseEntity.noContent().build();
    }

    private TelegramChat toApi(TelegramSeenChats.SeenChat chat) {
        TelegramChatType type = switch (chat.type()) {
            case "supergroup" -> TelegramChatType.SUPERGROUP;
            case "channel" -> TelegramChatType.CHANNEL;
            case "private" -> TelegramChatType.PRIVATE;
            default -> TelegramChatType.GROUP;
        };
        return new TelegramChat().chatId(chat.chatId()).title(chat.title()).type(type);
    }

    private static TelegramChatConfigList toApi(List<TelegramChatService.ChatConfig> configs) {
        return new TelegramChatConfigList().chats(configs.stream()
                .map(c -> new TelegramChatConfig().chatId(c.chatId()).title(c.title()).enabled(c.enabled()))
                .toList());
    }

    private static List<TelegramChatService.ChatConfig> fromApi(TelegramChatConfigList body) {
        return body.getChats().stream()
                .map(c -> new TelegramChatService.ChatConfig(c.getChatId(), c.getTitle(),
                        Boolean.TRUE.equals(c.getEnabled())))
                .toList();
    }
}
