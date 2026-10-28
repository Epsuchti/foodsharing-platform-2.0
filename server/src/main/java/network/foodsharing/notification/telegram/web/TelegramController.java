package network.foodsharing.notification.telegram.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.TelegramApi;
import network.foodsharing.api.model.TelegramChat;
import network.foodsharing.api.model.TelegramChatConfigList;
import network.foodsharing.api.model.TelegramChatTest;
import network.foodsharing.api.model.TelegramLinkToken;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telegram linking and alert chats (M4 B3).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class TelegramController implements TelegramApi {

    @Override
    public ResponseEntity<TelegramLinkToken> createTelegramLinkToken() {
        throw ApiException.notImplemented("createTelegramLinkToken");
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> getRegionTelegramChats(UUID regionId) {
        throw ApiException.notImplemented("getRegionTelegramChats");
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> getStoreTelegramChats(UUID storeId) {
        throw ApiException.notImplemented("getStoreTelegramChats");
    }

    @Override
    public ResponseEntity<List<TelegramChat>> listTelegramChats() {
        throw ApiException.notImplemented("listTelegramChats");
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> putRegionTelegramChats(UUID regionId, TelegramChatConfigList telegramChatConfigList) {
        throw ApiException.notImplemented("putRegionTelegramChats");
    }

    @Override
    public ResponseEntity<TelegramChatConfigList> putStoreTelegramChats(UUID storeId, TelegramChatConfigList telegramChatConfigList) {
        throw ApiException.notImplemented("putStoreTelegramChats");
    }

    @Override
    public ResponseEntity<Void> sendTelegramTest() {
        throw ApiException.notImplemented("sendTelegramTest");
    }

    @Override
    public ResponseEntity<Void> testRegionTelegramChat(UUID regionId, TelegramChatTest telegramChatTest) {
        throw ApiException.notImplemented("testRegionTelegramChat");
    }

    @Override
    public ResponseEntity<Void> testStoreTelegramChat(UUID storeId, TelegramChatTest telegramChatTest) {
        throw ApiException.notImplemented("testStoreTelegramChat");
    }

    @Override
    public ResponseEntity<Void> unlinkTelegram() {
        throw ApiException.notImplemented("unlinkTelegram");
    }
}
