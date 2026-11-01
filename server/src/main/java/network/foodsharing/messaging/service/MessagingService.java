package network.foodsharing.messaging.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.realtime.RealtimeEvent;
import network.foodsharing.common.realtime.RealtimeEventType;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.ConversationMember;
import network.foodsharing.messaging.domain.ConversationMessage;
import network.foodsharing.messaging.domain.MessageEditRules;
import network.foodsharing.messaging.domain.MessagingAction;
import network.foodsharing.messaging.repository.ConversationMemberRepository;
import network.foodsharing.messaging.repository.ConversationMessageRepository;
import network.foodsharing.messaging.repository.ConversationRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversations and messages (A33): DIRECT/GROUP lookup and creation, sending with a per-conversation seq under the
 * conversation row lock, idempotent client keys, read state, group member edits, system messages, and the
 * notifications of a new message (push per message to members without a realtime connection, digest mail), and
 * editing / deleting messages (sender within {@code messaging.edit-window-hours}, moderators delete with a reason).
 */
@Service
public class MessagingService {

    static final int MAX_MEMBERS = 100;
    static final int PREVIEW_LENGTH = 300;
    /** Minimum length of a moderator's delete reason (contract MessageDelete.reason 10..500). */
    static final int MIN_REASON_LENGTH = 10;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationMessageRepository messages;
    private final UserService users;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final SlidingWindowRateLimiter sendLimiter;
    private final ChatModeration moderation;
    private final ConfigurationService configuration;

    public MessagingService(ConversationRepository conversations, ConversationMemberRepository members,
                            ConversationMessageRepository messages, UserService users,
                            NotificationService notifications, RealtimePublisher realtime, HistoryService history,
                            AppProperties properties, Clock clock, ChatModeration moderation,
                            ConfigurationService configuration) {
        this.moderation = moderation;
        this.configuration = configuration;
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.realtime = realtime;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
        this.sendLimiter = new SlidingWindowRateLimiter("messages", clock,
                Math.max(1, properties.getMessaging().getMaxMessagesPerMinute()), Duration.ofMinutes(1));
    }

    // ------------------------------------------------------------------------------------------- access

    /** @throws ApiException 404 CONVERSATION_NOT_FOUND, 403 NOT_CONVERSATION_MEMBER */
    @Transactional(readOnly = true)
    public ConversationMember requireMember(UUID userId, UUID conversationId) {
        if (!conversations.existsById(conversationId)) {
            throw ApiException.notFound(ApiErrorCode.CONVERSATION_NOT_FOUND);
        }
        return members.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.forbidden(ApiErrorCode.NOT_CONVERSATION_MEMBER));
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID userId, UUID conversationId) {
        return members.existsByConversationIdAndUserId(conversationId, userId);
    }

    // ------------------------------------------------------------------------------------ create/lookup

    /**
     * Upstream {@code getOrCreateConversation}: the conversation with exactly the caller plus {@code memberIds} and no
     * name is reused; otherwise a DIRECT (one other member) or GROUP conversation is created.
     *
     * @throws ApiException 400 TOO_MANY_MEMBERS / VALIDATION_FAILED, 404 MEMBER_NOT_FOUND
     */
    @Transactional
    public Conversation lookup(UUID actorId, Collection<UUID> memberIds) {
        Set<UUID> others = otherMembers(actorId, memberIds);
        Set<UUID> wanted = new HashSet<>(others);
        wanted.add(actorId);
        Optional<Conversation> existing = findWithExactMembers(actorId, wanted);
        if (existing.isPresent()) {
            return existing.get();
        }
        return create(actorId, others.size() == 1 ? ConversationKind.DIRECT : ConversationKind.GROUP, null, wanted);
    }

    /** A new GROUP conversation (always new, optionally named). */
    @Transactional
    public Conversation createGroup(UUID actorId, Collection<UUID> memberIds, String name) {
        Set<UUID> wanted = new LinkedHashSet<>();
        wanted.add(actorId);
        wanted.addAll(otherMembers(actorId, memberIds));
        return create(actorId, ConversationKind.GROUP, blankToNull(name), wanted);
    }

    /** A locked conversation managed by a sync (store team/jumper, region); members are synced separately. */
    @Transactional
    public Conversation createLocked(ConversationKind kind) {
        Conversation conversation = new Conversation();
        conversation.setKind(kind);
        conversation.setLocked(true);
        conversation = conversations.save(conversation);
        history.record(history.event(MessagingAction.CONVERSATION_CREATED, conversation.getId())
                .metadata(Map.of("kind", kind.name())).build());
        return conversation;
    }

    private Conversation create(UUID actorId, ConversationKind kind, String name, Set<UUID> memberIds) {
        Conversation conversation = new Conversation();
        conversation.setKind(kind);
        conversation.setName(name);
        conversation = conversations.save(conversation);
        Instant now = clock.instant();
        for (UUID userId : memberIds) {
            members.save(member(conversation, userId, now));
        }
        history.record(history.event(MessagingAction.CONVERSATION_CREATED, conversation.getId())
                .secondarySubject(actorId).metadata(Map.of("kind", kind.name(), "members", memberIds.size())).build());
        return conversation;
    }

    private Set<UUID> otherMembers(UUID actorId, Collection<UUID> memberIds) {
        Set<UUID> others = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        others.remove(actorId);
        if (others.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (others.size() >= MAX_MEMBERS) {
            throw ApiException.badRequest(ApiErrorCode.TOO_MANY_MEMBERS);
        }
        if (users.findActive(others).size() != others.size()) {
            throw ApiException.notFound(ApiErrorCode.MEMBER_NOT_FOUND);
        }
        return others;
    }

    private Optional<Conversation> findWithExactMembers(UUID actorId, Set<UUID> wanted) {
        List<UUID> candidates = members.conversationIdsOf(actorId);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, Set<UUID>> byConversation = members.findByConversationIdIn(candidates).stream()
                .collect(Collectors.groupingBy(ConversationMember::getConversationId,
                        Collectors.mapping(ConversationMember::getUserId, Collectors.toSet())));
        return byConversation.entrySet().stream().filter(entry -> entry.getValue().equals(wanted))
                .map(entry -> conversations.findById(entry.getKey()).orElse(null))
                .filter(c -> c != null && c.getName() == null
                        && (c.getKind() == ConversationKind.DIRECT || c.getKind() == ConversationKind.GROUP))
                .findFirst();
    }

    // ---------------------------------------------------------------------------------------- messages

    /**
     * Sends a message; resending the same {@code clientKey} returns the stored message.
     *
     * @throws ApiException 400 MESSAGE_EMPTY / MESSAGE_TOO_LONG, 403 NOT_CONVERSATION_MEMBER, 404 CONVERSATION_NOT_FOUND,
     *                      429 MESSAGE_RATE_LIMITED (more than {@code app.messaging.max-messages-per-minute})
     */
    @Transactional
    @NoHistory(reason = "chat messages are content, not actions (A33)")
    public ConversationMessage send(UUID senderId, UUID conversationId, String body, String clientKey) {
        requireMember(senderId, conversationId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.MESSAGE_EMPTY);
        }
        if (text.length() > properties.getMessaging().getMaxMessageLength()) {
            throw ApiException.badRequest(ApiErrorCode.MESSAGE_TOO_LONG);
        }
        String key = blankToNull(clientKey);
        if (key != null) {
            Optional<ConversationMessage> stored = messages.findByConversationIdAndSenderIdAndClientKey(conversationId,
                    senderId, key);
            if (stored.isPresent()) {
                return stored.get();
            }
        }
        if (!sendLimiter.tryAcquire(senderId.toString())) {
            throw ApiException.tooManyRequests(ApiErrorCode.MESSAGE_RATE_LIMITED, sendLimiter.window());
        }
        return append(conversationId, senderId, text, key);
    }

    /** A system message ("foodsharing", sender {@code null}) in any conversation, e.g. open-slot adverts. */
    @Transactional
    public ConversationMessage postSystem(UUID conversationId, String text) {
        ConversationMessage message = append(conversationId, null, text.strip(), null);
        history.record(history.event(MessagingAction.SYSTEM_MESSAGE_POSTED, conversationId)
                .metadata(Map.of("seq", message.getSeq())).build());
        return message;
    }

    /** A direct message from one user to another (TeamChatPort, store team messages). */
    @Transactional
    @NoHistory(reason = "lookup records CONVERSATION_CREATED; messages are content")
    public ConversationMessage sendDirect(UUID fromUserId, UUID toUserId, String text) {
        Conversation conversation = lookup(fromUserId, List.of(toUserId));
        return send(fromUserId, conversation.getId(), text, null);
    }

    private ConversationMessage append(UUID conversationId, UUID senderId, String text, String clientKey) {
        Conversation conversation = conversations.findForUpdate(conversationId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.CONVERSATION_NOT_FOUND));
        Instant now = clock.instant();
        long seq = conversation.getLastSeq() + 1;
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setSeq(seq);
        message.setSenderId(senderId);
        message.setBody(text);
        message.setClientKey(clientKey);
        message.setSentAt(now);
        message = messages.save(message);
        conversation.setLastSeq(seq);
        conversation.setLastMessageAt(now);
        conversation.setLastMessagePreview(preview(text));
        conversation.setLastSenderId(senderId);
        List<ConversationMember> all = members.findByConversationId(conversationId);
        all.stream().filter(m -> m.getUserId().equals(senderId)).forEach(m -> m.setLastReadSeq(seq));
        Set<UUID> others = all.stream().map(ConversationMember::getUserId).filter(id -> !id.equals(senderId))
                .collect(Collectors.toSet());
        realtime.toUsers(others, new RealtimeEvent(RealtimeEventType.CONVERSATION_MESSAGE, now, payload(message)));
        if (senderId != null) {
            // the sender's other devices may edit and delete a message they just sent
            Map<String, Object> own = payload(message);
            own.put("mayEdit", true);
            own.put("mayDelete", true);
            realtime.toUsers(Set.of(senderId), new RealtimeEvent(RealtimeEventType.CONVERSATION_MESSAGE, now, own));
        }
        notifyRecipients(conversation, message, all.stream().filter(m -> !m.getUserId().equals(senderId)).toList(), now);
        return message;
    }

    static Map<String, Object> payload(ConversationMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", message.getConversationId().toString());
        payload.put("id", message.getId().toString());
        payload.put("seq", message.getSeq());
        if (message.getSenderId() != null) {
            payload.put("senderId", message.getSenderId().toString());
        }
        payload.put("body", message.getBody());
        payload.put("sentAt", message.getSentAt().toString());
        if (message.getClientKey() != null) {
            payload.put("clientKey", message.getClientKey());
        }
        if (message.getEditedAt() != null) {
            payload.put("editedAt", message.getEditedAt().toString());
        }
        if (message.getDeletedAt() != null) {
            payload.put("deletedAt", message.getDeletedAt().toString());
            payload.put("deletedByModerator", message.isDeletedByModerator());
        }
        return payload;
    }

    // ---------------------------------------------------------------------------------- edit / delete

    /** The configured edit window ({@code messaging.edit-window-hours}, default 24). */
    public Duration editWindow() {
        return Duration.ofHours(Math.max(0, configuration.integer(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS)));
    }

    /** What the viewer may do with the messages of one conversation (computed once per request). */
    @Transactional(readOnly = true)
    public MessagePermissions permissions(SessionPrincipal viewer, UUID conversationId) {
        return new MessagePermissions(viewer.userId(), moderation.isModerator(viewer, conversationId), clock.instant(),
                editWindow());
    }

    /**
     * Edits an own message within the edit window; records the text before and after (A33 amended).
     *
     * @throws ApiException 400 MESSAGE_EMPTY / MESSAGE_TOO_LONG, 403 NOT_CONVERSATION_MEMBER / MESSAGE_NOT_EDITABLE,
     *                      404 CONVERSATION_NOT_FOUND / MESSAGE_NOT_FOUND, 409 MESSAGE_EDIT_WINDOW_EXPIRED /
     *                      MESSAGE_DELETED
     */
    @Transactional
    public ConversationMessage editMessage(SessionPrincipal actor, UUID conversationId, UUID messageId, String body) {
        requireMember(actor.userId(), conversationId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.MESSAGE_EMPTY);
        }
        if (text.length() > properties.getMessaging().getMaxMessageLength()) {
            throw ApiException.badRequest(ApiErrorCode.MESSAGE_TOO_LONG);
        }
        Conversation conversation = lock(conversationId);
        ConversationMessage message = requireMessage(conversationId, messageId);
        Instant now = clock.instant();
        MessageEditRules.Verdict verdict = MessageEditRules.edit(actor.userId(), message, now, editWindow());
        if (verdict != MessageEditRules.Verdict.ALLOWED) {
            throw refused(verdict);
        }
        String original = message.getBody();
        if (original.equals(text)) {
            return message;
        }
        message.setBody(text);
        message.setEditedAt(now);
        history.record(history.event(MessagingAction.MESSAGE_EDITED, conversationId).secondarySubject(messageId)
                .oldValue(original).newValue(text).metadata(Map.of("seq", message.getSeq())).build());
        if (conversation.getLastSeq() == message.getSeq()) {
            conversation.setLastMessagePreview(preview(text));
        }
        publishUpdate(conversationId, message, now);
        return message;
    }

    /**
     * Deletes a message: the sender within the edit window, or a moderator ({@link ChatModeration}) at any time with
     * a reason of at least {@value #MIN_REASON_LENGTH} characters. The row and its seq stay, the body becomes
     * {@code ""}; the original text goes to the history.
     *
     * @throws ApiException 400 DELETE_REASON_REQUIRED, 403 NOT_CONVERSATION_MEMBER / MESSAGE_NOT_EDITABLE,
     *                      404 CONVERSATION_NOT_FOUND / MESSAGE_NOT_FOUND, 409 MESSAGE_EDIT_WINDOW_EXPIRED /
     *                      MESSAGE_DELETED
     */
    @Transactional
    public ConversationMessage deleteMessage(SessionPrincipal actor, UUID conversationId, UUID messageId,
                                             String reason) {
        requireMember(actor.userId(), conversationId);
        Conversation conversation = lock(conversationId);
        boolean moderator = moderation.isModerator(actor, conversation);
        ConversationMessage message = requireMessage(conversationId, messageId);
        Instant now = clock.instant();
        MessageEditRules.Verdict verdict = MessageEditRules.delete(actor.userId(), message, now, editWindow(),
                moderator);
        String why = null;
        if (verdict == MessageEditRules.Verdict.ALLOWED_AS_MODERATOR) {
            why = blankToNull(reason);
            if (why == null || why.length() < MIN_REASON_LENGTH) {
                throw ApiException.badRequest(ApiErrorCode.DELETE_REASON_REQUIRED);
            }
        } else if (verdict != MessageEditRules.Verdict.ALLOWED) {
            throw refused(verdict);
        }
        String original = message.getBody();
        message.setBody("");
        message.setDeletedAt(now);
        message.setDeletedBy(actor.userId());
        HistoryEvent.Builder event = history.event(MessagingAction.MESSAGE_DELETED, conversationId)
                .secondarySubject(messageId).oldValue(original)
                .metadata(Map.of("seq", message.getSeq(), "moderator", why != null));
        if (why != null) {
            event.reason(why);
        }
        history.record(event.build());
        if (conversation.getLastSeq() == message.getSeq()) {
            conversation.setLastMessagePreview("");
        }
        publishUpdate(conversationId, message, now);
        return message;
    }

    private Conversation lock(UUID conversationId) {
        return conversations.findForUpdate(conversationId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.CONVERSATION_NOT_FOUND));
    }

    private ConversationMessage requireMessage(UUID conversationId, UUID messageId) {
        return messages.findById(messageId).filter(m -> m.getConversationId().equals(conversationId))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.MESSAGE_NOT_FOUND));
    }

    private static ApiException refused(MessageEditRules.Verdict verdict) {
        return switch (verdict) {
            case DELETED -> ApiException.conflict(ApiErrorCode.MESSAGE_DELETED);
            case WINDOW_EXPIRED -> ApiException.conflict(ApiErrorCode.MESSAGE_EDIT_WINDOW_EXPIRED);
            default -> ApiException.forbidden(ApiErrorCode.MESSAGE_NOT_EDITABLE);
        };
    }

    /** CONVERSATION_MESSAGE_UPDATED to all members (after commit); the viewer flags stay with each client. */
    private void publishUpdate(UUID conversationId, ConversationMessage message, Instant now) {
        Set<UUID> ids = members.findByConversationId(conversationId).stream().map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        realtime.toUsers(ids, new RealtimeEvent(RealtimeEventType.CONVERSATION_MESSAGE_UPDATED, now, payload(message)));
    }

    private static String preview(String text) {
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) : text;
    }

    /** A33: push per message to members without a realtime connection; mail at most once per digest window. */
    private void notifyRecipients(Conversation conversation, ConversationMessage message,
                                  List<ConversationMember> recipients, Instant now) {
        if (recipients.isEmpty()) {
            return;
        }
        Set<UUID> connected = realtime.connectedUsers();
        String sender = message.getSenderId() == null ? "foodsharing"
                : users.findActive(message.getSenderId()).map(u -> u.getDisplayName()).orElse("foodsharing");
        List<UUID> offline = recipients.stream().map(ConversationMember::getUserId).filter(id -> !connected.contains(id))
                .toList();
        if (!offline.isEmpty()) {
            notifications.pushOnly(request(offline, conversation, message, sender).build());
        }
        Duration digest = Duration.ofMinutes(properties.getMessaging().getMailDigestMinutes());
        List<ConversationMember> mailable = recipients.stream()
                .filter(m -> m.getLastMailedAt() == null || !m.getLastMailedAt().plus(digest).isAfter(now)).toList();
        if (mailable.isEmpty()) {
            return;
        }
        Map<UUID, ConversationMember> byUser = new HashMap<>();
        mailable.forEach(m -> byUser.put(m.getUserId(), m));
        Set<UUID> mailed = notifications.mailOnly(request(new ArrayList<>(byUser.keySet()), conversation, message, sender)
                .mail("messaging/new-message").build());
        mailed.forEach(userId -> byUser.get(userId).setLastMailedAt(now));
    }

    private static NotificationRequest.Builder request(Collection<UUID> recipients, Conversation conversation,
                                                       ConversationMessage message, String sender) {
        String body = message.getBody();
        return NotificationRequest.to(recipients, NotificationType.NEW_MESSAGE)
                .param("conversationId", conversation.getId().toString()).param("userName", sender)
                .param("message", body.length() > 200 ? body.substring(0, 200) + "…" : body)
                .link("/messages/" + conversation.getId()).identifier("NEW_MESSAGE-" + conversation.getId())
                .icon("mail");
    }

    // -------------------------------------------------------------------------------------- read state

    /** Marks everything up to {@code lastReadSeq} (default: all) read; other devices get CONVERSATION_READ. */
    @Transactional
    @NoHistory(reason = "read state is not an action")
    public long markRead(UUID userId, UUID conversationId, Long lastReadSeq) {
        ConversationMember member = requireMember(userId, conversationId);
        Conversation conversation = conversations.findById(conversationId).orElseThrow();
        long target = lastReadSeq == null ? conversation.getLastSeq()
                : Math.max(0, Math.min(lastReadSeq, conversation.getLastSeq()));
        if (target > member.getLastReadSeq()) {
            member.setLastReadSeq(target);
        }
        if (member.getLastReadSeq() >= conversation.getLastSeq()) {
            member.setLastMailedAt(null);
        }
        realtime.toUsers(Set.of(userId), new RealtimeEvent(RealtimeEventType.CONVERSATION_READ, clock.instant(),
                Map.of("conversationId", conversationId.toString(), "lastReadSeq", member.getLastReadSeq())));
        return member.getLastReadSeq();
    }

    // -------------------------------------------------------------------------------- group management

    /** @throws ApiException 409 CONVERSATION_LOCKED */
    @Transactional
    public Conversation rename(UUID actorId, UUID conversationId, String name) {
        requireMember(actorId, conversationId);
        Conversation conversation = conversations.findById(conversationId).orElseThrow();
        if (conversation.isLocked()) {
            throw ApiException.conflict(ApiErrorCode.CONVERSATION_LOCKED);
        }
        String newName = blankToNull(name);
        history.record(history.event(MessagingAction.RENAMED, conversationId)
                .change("name", conversation.getName(), newName).build());
        conversation.setName(newName);
        updated(conversation);
        return conversation;
    }

    /** @throws ApiException 409 CONVERSATION_LOCKED / CONVERSATION_NOT_GROUP, 404 MEMBER_NOT_FOUND */
    @Transactional
    public Conversation addMember(UUID actorId, UUID conversationId, UUID userId) {
        Conversation conversation = requireEditableGroup(actorId, conversationId);
        if (users.findActive(userId).isEmpty()) {
            throw ApiException.notFound(ApiErrorCode.MEMBER_NOT_FOUND);
        }
        if (members.countByConversationId(conversationId) >= MAX_MEMBERS) {
            throw ApiException.badRequest(ApiErrorCode.TOO_MANY_MEMBERS);
        }
        if (!members.existsByConversationIdAndUserId(conversationId, userId)) {
            members.save(member(conversation, userId, clock.instant()));
            history.record(history.event(MessagingAction.MEMBER_ADDED, conversationId).secondarySubject(userId).build());
            updated(conversation);
        }
        return conversation;
    }

    /** Removes a member; removing oneself = leaving. */
    @Transactional
    public void removeMember(UUID actorId, UUID conversationId, UUID userId) {
        Conversation conversation = requireEditableGroup(actorId, conversationId);
        ConversationMember member = members.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.MEMBER_NOT_FOUND));
        members.delete(member);
        history.record(history.event(MessagingAction.MEMBER_REMOVED, conversationId).secondarySubject(userId)
                .metadata(Map.of("self", actorId.equals(userId))).build());
        updated(conversation);
    }

    private Conversation requireEditableGroup(UUID actorId, UUID conversationId) {
        requireMember(actorId, conversationId);
        Conversation conversation = conversations.findById(conversationId).orElseThrow();
        if (conversation.isLocked()) {
            throw ApiException.conflict(ApiErrorCode.CONVERSATION_LOCKED);
        }
        if (conversation.getKind() != ConversationKind.GROUP) {
            throw ApiException.conflict(ApiErrorCode.CONVERSATION_NOT_GROUP);
        }
        return conversation;
    }

    private void updated(Conversation conversation) {
        Set<UUID> ids = members.findByConversationId(conversation.getId()).stream().map(ConversationMember::getUserId)
                .collect(Collectors.toSet());
        realtime.toUsers(ids, new RealtimeEvent(RealtimeEventType.CONVERSATION_UPDATED, clock.instant(),
                Map.of("conversationId", conversation.getId().toString())));
    }

    // ------------------------------------------------------------------------------------------- sync

    /**
     * Makes the members of a locked conversation exactly {@code wanted} (store and region chats). New members start
     * with everything read.
     *
     * @return number of added plus removed members
     */
    @Transactional
    public int syncMembers(UUID conversationId, Set<UUID> wanted) {
        Conversation conversation = conversations.findById(conversationId).orElseThrow();
        List<ConversationMember> current = members.findByConversationId(conversationId);
        Set<UUID> present = current.stream().map(ConversationMember::getUserId).collect(Collectors.toSet());
        int changes = 0;
        for (ConversationMember member : current) {
            if (!wanted.contains(member.getUserId())) {
                members.delete(member);
                changes++;
            }
        }
        Instant now = clock.instant();
        for (UUID userId : wanted) {
            if (!present.contains(userId)) {
                ConversationMember member = member(conversation, userId, now);
                member.setLastReadSeq(conversation.getLastSeq());
                members.save(member);
                changes++;
            }
        }
        if (changes > 0) {
            history.record(history.event(MessagingAction.MEMBERS_SYNCED, conversationId)
                    .metadata(Map.of("changes", changes, "members", wanted.size())).build());
            updated(conversation);
        }
        return changes;
    }

    private static ConversationMember member(Conversation conversation, UUID userId, Instant now) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversation.getId());
        member.setUserId(userId);
        member.setJoinedAt(now);
        return member;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
