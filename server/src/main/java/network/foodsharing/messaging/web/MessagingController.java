package network.foodsharing.messaging.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.MessagingApi;
import network.foodsharing.api.model.ChatMessage;
import network.foodsharing.api.model.ConversationCreate;
import network.foodsharing.api.model.ConversationDetail;
import network.foodsharing.api.model.ConversationKind;
import network.foodsharing.api.model.ConversationLookup;
import network.foodsharing.api.model.ConversationPage;
import network.foodsharing.api.model.ConversationReadUpdate;
import network.foodsharing.api.model.ConversationRename;
import network.foodsharing.api.model.ConversationUnreadCount;
import network.foodsharing.api.model.MessageCreate;
import network.foodsharing.api.model.MessagePreview;
import network.foodsharing.api.model.RegionConversation;
import network.foodsharing.api.model.RegionConversationUpdate;
import network.foodsharing.api.model.StoreConversations;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationMessage;
import network.foodsharing.messaging.service.ConversationLinkService;
import network.foodsharing.messaging.service.ConversationQueryService;
import network.foodsharing.messaging.service.ConversationQueryService.ConversationView;
import network.foodsharing.messaging.service.MessagingService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Conversations and chats (N1, S10; docs/conventions.md A33). */
@RestController
public class MessagingController implements MessagingApi {

    private final MessagingService messaging;
    private final ConversationQueryService queries;
    private final ConversationLinkService links;
    private final CurrentActorService currentActor;
    private final UserMapper userMapper;

    public MessagingController(MessagingService messaging, ConversationQueryService queries,
                               ConversationLinkService links, CurrentActorService currentActor, UserMapper userMapper) {
        this.messaging = messaging;
        this.queries = queries;
        this.links = links;
        this.currentActor = currentActor;
        this.userMapper = userMapper;
    }

    @Override
    public ResponseEntity<ConversationPage> listConversations(Integer page, Integer size) {
        UUID me = currentActor.requireUserId();
        Page<ConversationView> views = queries.page(me, page == null ? 0 : page, size == null ? 20 : size);
        return ResponseEntity.ok(new ConversationPage().items(views.getContent().stream().map(this::toApi).toList())
                .page(PageMapper.toPageMeta(views)));
    }

    @Override
    public ResponseEntity<ConversationUnreadCount> getConversationUnreadCount() {
        ConversationQueryService.UnreadCount count = queries.unreadCount(currentActor.requireUserId());
        return ResponseEntity.ok(new ConversationUnreadCount().conversations(count.conversations())
                .messages(count.messages()));
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.Conversation> lookupConversation(ConversationLookup lookup) {
        UUID me = currentActor.requireUserId();
        Conversation conversation = messaging.lookup(me, lookup.getMemberIds());
        return ResponseEntity.ok(toApi(queries.view(me, conversation.getId())));
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.Conversation> createConversation(ConversationCreate create) {
        UUID me = currentActor.requireUserId();
        Conversation conversation = messaging.createGroup(me, create.getMemberIds(), create.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(queries.view(me, conversation.getId())));
    }

    @Override
    public ResponseEntity<ConversationDetail> getConversation(UUID conversationId) {
        UUID me = currentActor.requireUserId();
        ConversationView view = queries.view(me, conversationId);
        network.foodsharing.api.model.Conversation c = toApi(view);
        ConversationDetail detail = new ConversationDetail().id(c.getId()).kind(c.getKind()).name(c.getName())
                .customName(c.getCustomName()).locked(c.getLocked()).members(c.getMembers())
                .memberCount(c.getMemberCount()).lastMessage(c.getLastMessage()).lastMessageAt(c.getLastMessageAt())
                .unreadCount(c.getUnreadCount()).lastReadSeq(c.getLastReadSeq()).lastSeq(c.getLastSeq())
                .storeId(c.getStoreId()).regionId(c.getRegionId())
                .messages(queries.latest(me, conversationId).stream().map(MessagingController::toApi).toList());
        return ResponseEntity.ok(detail);
    }

    @Override
    public ResponseEntity<List<ChatMessage>> listMessages(UUID conversationId, Long beforeSeq, Long afterSeq,
                                                          Integer limit) {
        return ResponseEntity.ok(queries.messages(currentActor.requireUserId(), conversationId, beforeSeq, afterSeq,
                limit == null ? 30 : limit).stream().map(MessagingController::toApi).toList());
    }

    @Override
    public ResponseEntity<ChatMessage> sendMessage(UUID conversationId, MessageCreate create) {
        return ResponseEntity.ok(toApi(messaging.send(currentActor.requireUserId(), conversationId, create.getBody(),
                create.getClientKey())));
    }

    @Override
    public ResponseEntity<Void> markConversationRead(UUID conversationId, ConversationReadUpdate update) {
        messaging.markRead(currentActor.requireUserId(), conversationId, update == null ? null : update.getLastReadSeq());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.Conversation> renameConversation(UUID conversationId,
                                                                                         ConversationRename rename) {
        UUID me = currentActor.requireUserId();
        messaging.rename(me, conversationId, rename.getName());
        return ResponseEntity.ok(toApi(queries.view(me, conversationId)));
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.Conversation> addConversationMember(UUID conversationId,
                                                                                            UUID userId) {
        UUID me = currentActor.requireUserId();
        messaging.addMember(me, conversationId, userId);
        return ResponseEntity.ok(toApi(queries.view(me, conversationId)));
    }

    @Override
    public ResponseEntity<Void> removeConversationMember(UUID conversationId, UUID userId) {
        messaging.removeMember(currentActor.requireUserId(), conversationId, userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<StoreConversations> getStoreConversations(UUID storeId) {
        ConversationLinkService.StoreChats chats = links.storeChats(currentActor.requirePrincipal(), storeId);
        return ResponseEntity.ok(new StoreConversations().teamConversationId(chats.teamConversationId())
                .jumperConversationId(chats.jumperConversationId()));
    }

    @Override
    public ResponseEntity<RegionConversation> getRegionConversation(UUID regionId) {
        return ResponseEntity.ok(toApi(links.regionChat(currentActor.requirePrincipal(), regionId)));
    }

    @Override
    public ResponseEntity<RegionConversation> setRegionConversationEnabled(UUID regionId,
                                                                           RegionConversationUpdate update) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        return ResponseEntity.ok(toApi(links.setRegionChatEnabled(actor, regionId, update.getEnabled())));
    }

    // ------------------------------------------------------------------------------------------ mapping

    private network.foodsharing.api.model.Conversation toApi(ConversationView view) {
        Conversation c = view.conversation();
        network.foodsharing.api.model.Conversation api = new network.foodsharing.api.model.Conversation()
                .id(c.getId()).kind(ConversationKind.fromValue(c.getKind().name())).name(view.name())
                .customName(c.getName()).locked(c.isLocked())
                .members(view.members().stream().map(userMapper::toSummary).toList()).memberCount(view.memberCount())
                .lastMessageAt(TimeMapper.toOffset(c.getLastMessageAt()))
                .unreadCount((int) Math.min(Integer.MAX_VALUE, view.unreadCount())).lastReadSeq(view.lastReadSeq())
                .lastSeq(c.getLastSeq()).storeId(view.storeId()).regionId(view.regionId());
        if (c.getLastMessageAt() != null) {
            api.lastMessage(new MessagePreview().seq(c.getLastSeq()).body(c.getLastMessagePreview() == null ? ""
                    : c.getLastMessagePreview()).senderId(c.getLastSenderId())
                    .at(TimeMapper.toOffset(c.getLastMessageAt())));
        }
        return api;
    }

    static ChatMessage toApi(ConversationMessage message) {
        return new ChatMessage().id(message.getId()).seq(message.getSeq()).senderId(message.getSenderId())
                .body(message.getBody()).sentAt(TimeMapper.toOffset(message.getSentAt()))
                .clientKey(message.getClientKey());
    }

    private static RegionConversation toApi(ConversationLinkService.RegionChat chat) {
        return new RegionConversation().conversationId(chat.conversationId()).enabled(chat.enabled())
                .mayToggle(chat.mayToggle());
    }
}
