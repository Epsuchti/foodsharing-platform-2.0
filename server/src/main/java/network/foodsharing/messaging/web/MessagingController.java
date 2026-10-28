package network.foodsharing.messaging.web;

import network.foodsharing.api.MessagingApi;
import network.foodsharing.api.model.ChatMessage;
import network.foodsharing.api.model.Conversation;
import network.foodsharing.api.model.ConversationCreate;
import network.foodsharing.api.model.ConversationDetail;
import network.foodsharing.api.model.ConversationLookup;
import network.foodsharing.api.model.ConversationPage;
import network.foodsharing.api.model.ConversationReadUpdate;
import network.foodsharing.api.model.ConversationRename;
import network.foodsharing.api.model.ConversationUnreadCount;
import network.foodsharing.api.model.MessageCreate;
import network.foodsharing.api.model.RegionConversation;
import network.foodsharing.api.model.RegionConversationUpdate;
import network.foodsharing.api.model.StoreConversations;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

/** Conversations and chats (N1, S10). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class MessagingController implements MessagingApi {

    @Override
    public ResponseEntity<Conversation> addConversationMember(UUID conversationId, UUID userId) {
        throw ApiException.notImplemented("addConversationMember");
    }

    @Override
    public ResponseEntity<Conversation> createConversation(ConversationCreate conversationCreate) {
        throw ApiException.notImplemented("createConversation");
    }

    @Override
    public ResponseEntity<ConversationDetail> getConversation(UUID conversationId) {
        throw ApiException.notImplemented("getConversation");
    }

    @Override
    public ResponseEntity<ConversationUnreadCount> getConversationUnreadCount() {
        throw ApiException.notImplemented("getConversationUnreadCount");
    }

    @Override
    public ResponseEntity<RegionConversation> getRegionConversation(UUID regionId) {
        throw ApiException.notImplemented("getRegionConversation");
    }

    @Override
    public ResponseEntity<StoreConversations> getStoreConversations(UUID storeId) {
        throw ApiException.notImplemented("getStoreConversations");
    }

    @Override
    public ResponseEntity<ConversationPage> listConversations(Integer page, Integer size) {
        throw ApiException.notImplemented("listConversations");
    }

    @Override
    public ResponseEntity<List<ChatMessage>> listMessages(UUID conversationId, Long beforeSeq, Long afterSeq, Integer limit) {
        throw ApiException.notImplemented("listMessages");
    }

    @Override
    public ResponseEntity<Conversation> lookupConversation(ConversationLookup conversationLookup) {
        throw ApiException.notImplemented("lookupConversation");
    }

    @Override
    public ResponseEntity<Void> markConversationRead(UUID conversationId, ConversationReadUpdate conversationReadUpdate) {
        throw ApiException.notImplemented("markConversationRead");
    }

    @Override
    public ResponseEntity<Void> removeConversationMember(UUID conversationId, UUID userId) {
        throw ApiException.notImplemented("removeConversationMember");
    }

    @Override
    public ResponseEntity<Conversation> renameConversation(UUID conversationId, ConversationRename conversationRename) {
        throw ApiException.notImplemented("renameConversation");
    }

    @Override
    public ResponseEntity<ChatMessage> sendMessage(UUID conversationId, MessageCreate messageCreate) {
        throw ApiException.notImplemented("sendMessage");
    }

    @Override
    public ResponseEntity<RegionConversation> setRegionConversationEnabled(UUID regionId, RegionConversationUpdate regionConversationUpdate) {
        throw ApiException.notImplemented("setRegionConversationEnabled");
    }
}
