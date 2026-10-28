package network.foodsharing.messaging.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.ConversationMember;
import network.foodsharing.messaging.domain.ConversationMessage;
import network.foodsharing.messaging.domain.RegionConversation;
import network.foodsharing.messaging.domain.StoreConversation;
import network.foodsharing.messaging.repository.ConversationMemberRepository;
import network.foodsharing.messaging.repository.ConversationMessageRepository;
import network.foodsharing.messaging.repository.ConversationRepository;
import network.foodsharing.messaging.repository.RegionConversationRepository;
import network.foodsharing.messaging.repository.StoreConversationRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of conversations (A33): my list with unread counts and resolved names, messages by seq. */
@Service
@Transactional(readOnly = true)
public class ConversationQueryService {

    static final int MEMBER_LIMIT = 50;
    static final int DETAIL_MESSAGES = 30;

    /** One conversation as the viewer sees it. */
    public record ConversationView(Conversation conversation, String name, List<User> members, int memberCount,
                                   long lastReadSeq, long unreadCount, UUID storeId, UUID regionId) {
    }

    public record UnreadCount(int conversations, int messages) {
    }

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationMessageRepository messages;
    private final StoreConversationRepository storeLinks;
    private final RegionConversationRepository regionLinks;
    private final MessagingService messaging;
    private final UserService users;
    private final StoreService stores;
    private final RegionService regions;
    private final MessageSource messageSource;

    public ConversationQueryService(ConversationRepository conversations, ConversationMemberRepository members,
                                    ConversationMessageRepository messages, StoreConversationRepository storeLinks,
                                    RegionConversationRepository regionLinks, MessagingService messaging,
                                    UserService users, StoreService stores, RegionService regions,
                                    MessageSource messageSource) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.storeLinks = storeLinks;
        this.regionLinks = regionLinks;
        this.messaging = messaging;
        this.users = users;
        this.stores = stores;
        this.regions = regions;
        this.messageSource = messageSource;
    }

    public Page<ConversationView> page(UUID viewerId, int page, int size) {
        Page<ConversationMember> mine = members.pageOf(viewerId, PageRequest.of(page, size));
        Map<UUID, ConversationView> views = views(viewerId, mine.getContent());
        return mine.map(m -> views.get(m.getConversationId()));
    }

    /** @throws ApiException 404 CONVERSATION_NOT_FOUND, 403 NOT_CONVERSATION_MEMBER */
    public ConversationView view(UUID viewerId, UUID conversationId) {
        ConversationMember me = messaging.requireMember(viewerId, conversationId);
        return views(viewerId, List.of(me)).get(conversationId);
    }

    public UnreadCount unreadCount(UUID viewerId) {
        List<ConversationMember> mine = members.findByUserId(viewerId);
        Map<UUID, Conversation> byId = conversations.findAllById(mine.stream().map(ConversationMember::getConversationId)
                .toList()).stream().collect(Collectors.toMap(Conversation::getId, c -> c));
        int conversationsWithUnread = 0;
        long unread = 0;
        for (ConversationMember m : mine) {
            Conversation c = byId.get(m.getConversationId());
            long count = c == null ? 0 : Math.max(0, c.getLastSeq() - m.getLastReadSeq());
            if (count > 0) {
                conversationsWithUnread++;
                unread += count;
            }
        }
        return new UnreadCount(conversationsWithUnread, (int) Math.min(Integer.MAX_VALUE, unread));
    }

    /** Ascending by seq; {@code afterSeq} wins over {@code beforeSeq}; without both the latest {@code limit}. */
    public List<ConversationMessage> messages(UUID viewerId, UUID conversationId, Long beforeSeq, Long afterSeq,
                                              int limit) {
        messaging.requireMember(viewerId, conversationId);
        PageRequest pageRequest = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        if (afterSeq != null) {
            return messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(conversationId, afterSeq, pageRequest);
        }
        List<ConversationMessage> latest = new ArrayList<>(beforeSeq != null
                ? messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(conversationId, beforeSeq, pageRequest)
                : messages.findByConversationIdOrderBySeqDesc(conversationId, pageRequest));
        latest.sort(Comparator.comparingLong(ConversationMessage::getSeq));
        return latest;
    }

    public List<ConversationMessage> latest(UUID viewerId, UUID conversationId) {
        return messages(viewerId, conversationId, null, null, DETAIL_MESSAGES);
    }

    private Map<UUID, ConversationView> views(UUID viewerId, List<ConversationMember> mine) {
        List<UUID> ids = mine.stream().map(ConversationMember::getConversationId).toList();
        Map<UUID, Conversation> byId = conversations.findAllById(ids).stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c));
        Map<UUID, List<UUID>> memberIds = members.findByConversationIdIn(ids).stream()
                .sorted(Comparator.comparing(ConversationMember::getJoinedAt))
                .collect(Collectors.groupingBy(ConversationMember::getConversationId,
                        Collectors.mapping(ConversationMember::getUserId, Collectors.toList())));
        Map<UUID, User> usersById = new HashMap<>();
        users.findAll(memberIds.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()))
                .forEach(u -> usersById.put(u.getId(), u));
        Map<UUID, StoreConversation> storeById = storeLinks.findByConversationIdIn(ids).stream()
                .collect(Collectors.toMap(StoreConversation::getConversationId, l -> l));
        Map<UUID, RegionConversation> regionById = regionLinks.findByConversationIdIn(ids).stream()
                .collect(Collectors.toMap(RegionConversation::getConversationId, l -> l));
        Locale locale = users.findActive(viewerId).map(u -> Locale.forLanguageTag(u.getPreferredLanguage() == null
                ? "de" : u.getPreferredLanguage().getValue())).orElse(Locale.GERMAN);
        Map<UUID, ConversationView> result = new HashMap<>();
        for (ConversationMember me : mine) {
            Conversation c = byId.get(me.getConversationId());
            List<UUID> all = memberIds.getOrDefault(c.getId(), List.of());
            List<User> shown = all.stream().limit(MEMBER_LIMIT).map(usersById::get).filter(u -> u != null).toList();
            StoreConversation store = storeById.get(c.getId());
            RegionConversation region = regionById.get(c.getId());
            String name = name(c, viewerId, all, usersById, store, region, locale);
            result.put(c.getId(), new ConversationView(c, name, shown, all.size(), me.getLastReadSeq(),
                    Math.max(0, c.getLastSeq() - me.getLastReadSeq()), store == null ? null : store.getStoreId(),
                    region == null ? null : region.getRegionId()));
        }
        return result;
    }

    private String name(Conversation c, UUID viewerId, List<UUID> memberIds, Map<UUID, User> usersById,
                        StoreConversation store, RegionConversation region, Locale locale) {
        if (store != null) {
            String storeName = stores.storesById(List.of(store.getStoreId())).stream().findFirst()
                    .map(s -> s.getName()).orElse("?");
            String key = store.getKind() == StoreChat.TEAM ? "messaging.store.team-name" : "messaging.store.jumper-name";
            return messageSource.getMessage(key, new Object[] {storeName}, storeName, locale);
        }
        if (region != null) {
            return regions.find(region.getRegionId()).map(r -> r.getName()).orElse("?");
        }
        if (c.getName() != null) {
            return c.getName();
        }
        String others = memberIds.stream().filter(id -> !id.equals(viewerId)).map(usersById::get)
                .filter(u -> u != null).map(User::getDisplayName).limit(5).collect(Collectors.joining(", "));
        if (c.getKind() == ConversationKind.GROUP && memberIds.size() > 6) {
            others += " +" + (memberIds.size() - 6);
        }
        return others.isEmpty() ? "—" : others;
    }
}
