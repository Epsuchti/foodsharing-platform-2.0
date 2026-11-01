package network.foodsharing.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.realtime.RealtimeEvent;
import network.foodsharing.common.realtime.RealtimeEventType;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.ConversationMessage;
import network.foodsharing.messaging.repository.ConversationMemberRepository;
import network.foodsharing.messaging.service.ChatModeration;
import network.foodsharing.messaging.service.MessagingService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** A33: lookup reuse, seq, idempotent client keys, locked/group rules, read state, push vs digest mail. */
@JpaSliceTest
@Import({MessagingService.class, UserService.class})
class MessagingServiceTest {

    @Autowired
    private MessagingService messaging;

    @Autowired
    private ConversationMemberRepository members;

    @Autowired
    private UserRepository users;

    @Autowired
    private MutableClock clock;

    @MockitoBean
    private NotificationService notifications;

    @MockitoBean
    private RealtimePublisher realtime;

    @MockitoBean
    private ChatModeration moderation;

    @MockitoBean
    private ConfigurationService configuration;

    @Autowired
    private HistoryEntryRepository history;

    private UUID me;
    private UUID bob;
    private UUID carol;

    @BeforeEach
    void setUp() {
        me = users.saveAndFlush(TestUsers.user("msg-me@test.local", Role.FOODSAVER)).getId();
        bob = users.saveAndFlush(TestUsers.user("msg-bob@test.local", Role.FOODSAVER)).getId();
        carol = users.saveAndFlush(TestUsers.user("msg-carol@test.local", Role.FOODSAVER)).getId();
        when(realtime.connectedUsers()).thenReturn(Set.of());
        when(notifications.mailOnly(any())).thenAnswer(i -> ((NotificationRequest) i.getArgument(0)).recipientIds());
        when(configuration.integer(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS)).thenReturn(24);
    }

    private static SessionPrincipal principal(UUID userId) {
        return new SessionPrincipal(userId, UUID.randomUUID(), Role.FOODSAVER, userId + "@test.local");
    }

    private List<HistoryEntry> historyOf(UUID conversationId, String action) {
        return history.findAll().stream()
                .filter(e -> conversationId.equals(e.getSubjectId()) && action.equals(e.getAction())).toList();
    }

    @Test
    void lookupReusesTheConversationWithExactlyTheseMembers() {
        Conversation direct = messaging.lookup(me, List.of(bob));
        assertThat(direct.getKind()).isEqualTo(ConversationKind.DIRECT);
        assertThat(messaging.lookup(bob, List.of(me)).getId()).isEqualTo(direct.getId());
        Conversation group = messaging.lookup(me, List.of(bob, carol));
        assertThat(group.getKind()).isEqualTo(ConversationKind.GROUP);
        assertThat(group.getId()).isNotEqualTo(direct.getId());
        assertThat(messaging.lookup(carol, List.of(bob, me)).getId()).isEqualTo(group.getId());
        Conversation named = messaging.createGroup(me, List.of(bob), "Planung");
        assertThat(messaging.lookup(me, List.of(bob)).getId()).isEqualTo(direct.getId()).isNotEqualTo(named.getId());
    }

    @Test
    void lookupValidatesMembers() {
        assertThatThrownBy(() -> messaging.lookup(me, List.of(UUID.randomUUID())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MEMBER_NOT_FOUND);
        assertThatThrownBy(() -> messaging.lookup(me, List.of(me)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void messagesGetIncreasingSeqAndClientKeysAreIdempotent() {
        UUID id = messaging.lookup(me, List.of(bob)).getId();
        ConversationMessage first = messaging.send(me, id, " Hallo ", "k1");
        ConversationMessage again = messaging.send(me, id, "Hallo", "k1");
        ConversationMessage second = messaging.send(bob, id, "Hoi", "k1");
        assertThat(first.getSeq()).isEqualTo(1);
        assertThat(first.getBody()).isEqualTo("Hallo");
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(second.getSeq()).isEqualTo(2);
        assertThat(members.findByConversationIdAndUserId(id, bob).orElseThrow().getLastReadSeq()).isEqualTo(2);
        assertThat(members.findByConversationIdAndUserId(id, me).orElseThrow().getLastReadSeq()).isEqualTo(1);
        assertThat(messaging.markRead(me, id, null)).isEqualTo(2);
        assertThat(messaging.markRead(me, id, 1L)).isEqualTo(2);
    }

    @Test
    void sendValidatesBodyAndMembership() {
        UUID id = messaging.lookup(me, List.of(bob)).getId();
        assertThatThrownBy(() -> messaging.send(me, id, "   ", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_EMPTY);
        assertThatThrownBy(() -> messaging.send(me, id, "x".repeat(10001), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_TOO_LONG);
        assertThatThrownBy(() -> messaging.send(carol, id, "hi", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.NOT_CONVERSATION_MEMBER);
        assertThatThrownBy(() -> messaging.send(me, UUID.randomUUID(), "hi", null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void groupRulesAndLockedConversations() {
        UUID direct = messaging.lookup(me, List.of(bob)).getId();
        assertThatThrownBy(() -> messaging.addMember(me, direct, carol))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.CONVERSATION_NOT_GROUP);
        UUID group = messaging.createGroup(me, List.of(bob), null).getId();
        messaging.addMember(bob, group, carol);
        messaging.removeMember(carol, group, carol);
        assertThat(messaging.isMember(carol, group)).isFalse();
        assertThat(messaging.rename(me, group, "Neu").getName()).isEqualTo("Neu");

        Conversation locked = messaging.createLocked(ConversationKind.STORE_TEAM);
        messaging.syncMembers(locked.getId(), Set.of(me, bob));
        assertThatThrownBy(() -> messaging.rename(me, locked.getId(), "x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.CONVERSATION_LOCKED);
        assertThatThrownBy(() -> messaging.addMember(me, locked.getId(), carol))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.CONVERSATION_LOCKED);
        ConversationMessage system = messaging.postSystem(locked.getId(), "Freier Platz morgen");
        assertThat(system.getSenderId()).isNull();
        assertThat(messaging.syncMembers(locked.getId(), Set.of(me, carol))).isEqualTo(2);
        assertThat(members.findByConversationIdAndUserId(locked.getId(), carol).orElseThrow().getLastReadSeq())
                .isEqualTo(1);
    }

    @Test
    void pushOnlyToOfflineMembersAndDigestMailAtMostEvery30Minutes() {
        UUID id = messaging.lookup(me, List.of(bob, carol)).getId();
        when(realtime.connectedUsers()).thenReturn(Set.of(carol));
        messaging.send(me, id, "eins", null);
        ArgumentCaptor<NotificationRequest> push = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).pushOnly(push.capture());
        assertThat(push.getValue().recipientIds()).containsExactly(bob);
        ArgumentCaptor<NotificationRequest> mail = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).mailOnly(mail.capture());
        assertThat(mail.getValue().recipientIds()).containsExactlyInAnyOrder(bob, carol);
        assertThat(mail.getValue().mailTemplate()).isEqualTo("messaging/new-message");

        reset(notifications);
        when(notifications.mailOnly(any())).thenAnswer(i -> ((NotificationRequest) i.getArgument(0)).recipientIds());
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(10)));
        messaging.send(me, id, "zwei", null);
        verify(notifications, never()).mailOnly(any());
        messaging.markRead(bob, id, null);
        messaging.send(me, id, "drei", null);
        ArgumentCaptor<NotificationRequest> afterRead = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(1)).mailOnly(afterRead.capture());
        assertThat(afterRead.getValue().recipientIds()).containsExactly(bob);
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(31)));
        messaging.send(me, id, "vier", null);
        verify(notifications, times(2)).mailOnly(any());
    }

    // ------------------------------------------------------------------------------ edit / delete (item 16)

    @Test
    void editRefreshesThePreviewRecordsOldAndNewTextAndPublishesTheUpdate() {
        UUID id = messaging.lookup(me, List.of(bob)).getId();
        ConversationMessage first = messaging.send(me, id, "Hallo Bob", null);
        ConversationMessage last = messaging.send(me, id, "Bis morgen", null);
        reset(realtime);

        ConversationMessage edited = messaging.editMessage(principal(me), id, last.getId(), " Bis übermorgen ");
        assertThat(edited.getBody()).isEqualTo("Bis übermorgen");
        assertThat(edited.getEditedAt()).isEqualTo(clock.instant());
        assertThat(messaging.lookup(me, List.of(bob)).getLastMessagePreview()).isEqualTo("Bis übermorgen");
        assertThat(historyOf(id, "MESSAGE_EDITED")).singleElement().satisfies(entry -> {
            assertThat(entry.getSecondarySubjectId()).isEqualTo(last.getId());
            assertThat(entry.getOldValue()).isEqualTo("Bis morgen");
            assertThat(entry.getNewValue()).isEqualTo("Bis übermorgen");
        });
        ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(realtime).toUsers(eq(Set.of(me, bob)), event.capture());
        assertThat(event.getValue().type()).isEqualTo(RealtimeEventType.CONVERSATION_MESSAGE_UPDATED);
        assertThat(event.getValue().payload()).containsEntry("body", "Bis übermorgen")
                .containsEntry("conversationId", id.toString()).containsKey("editedAt");

        // an older message: the preview stays; every edit is one history entry
        messaging.editMessage(principal(me), id, first.getId(), "Hallo Bob, wie geht's?");
        assertThat(messaging.lookup(me, List.of(bob)).getLastMessagePreview()).isEqualTo("Bis übermorgen");
        assertThat(historyOf(id, "MESSAGE_EDITED")).hasSize(2);
    }

    @Test
    void editRules() {
        UUID id = messaging.lookup(me, List.of(bob)).getId();
        ConversationMessage message = messaging.send(me, id, "Hallo", null);
        assertThatThrownBy(() -> messaging.editMessage(principal(bob), id, message.getId(), "x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_NOT_EDITABLE);
        assertThatThrownBy(() -> messaging.editMessage(principal(me), id, message.getId(), "  "))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_EMPTY);
        assertThatThrownBy(() -> messaging.editMessage(principal(carol), id, message.getId(), "x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.NOT_CONVERSATION_MEMBER);
        assertThatThrownBy(() -> messaging.editMessage(principal(me), id, UUID.randomUUID(), "x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_NOT_FOUND);
        clock.fixAt(message.getSentAt().plus(Duration.ofHours(24)).plusSeconds(1));
        assertThatThrownBy(() -> messaging.editMessage(principal(me), id, message.getId(), "x"))
                .isInstanceOf(ApiException.class).extracting("code")
                .isEqualTo(ApiErrorCode.MESSAGE_EDIT_WINDOW_EXPIRED);
        assertThatThrownBy(() -> messaging.deleteMessage(principal(me), id, message.getId(), null))
                .isInstanceOf(ApiException.class).extracting("code")
                .isEqualTo(ApiErrorCode.MESSAGE_EDIT_WINDOW_EXPIRED);
        // the window comes from the configuration
        when(configuration.integer(ConfigurationKey.MESSAGING_EDIT_WINDOW_HOURS)).thenReturn(48);
        assertThat(messaging.editMessage(principal(me), id, message.getId(), "Hallo zusammen").getBody())
                .isEqualTo("Hallo zusammen");
    }

    @Test
    void deleteKeepsTheRowClearsTheBodyAndADeletedMessageCannotBeEdited() {
        UUID id = messaging.lookup(me, List.of(bob)).getId();
        ConversationMessage message = messaging.send(me, id, "Falscher Chat", null);
        reset(realtime);
        ConversationMessage deleted = messaging.deleteMessage(principal(me), id, message.getId(), null);
        assertThat(deleted.getSeq()).isEqualTo(1);
        assertThat(deleted.getBody()).isEmpty();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.isDeletedByModerator()).isFalse();
        assertThat(messaging.lookup(me, List.of(bob)).getLastMessagePreview()).isEmpty();
        assertThat(historyOf(id, "MESSAGE_DELETED")).singleElement().satisfies(entry -> {
            assertThat(entry.getOldValue()).isEqualTo("Falscher Chat");
            assertThat(entry.getReason()).isNull();
        });
        verify(realtime).toUsers(eq(Set.of(me, bob)), any(RealtimeEvent.class));
        assertThatThrownBy(() -> messaging.editMessage(principal(me), id, message.getId(), "x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_DELETED);
        assertThatThrownBy(() -> messaging.deleteMessage(principal(me), id, message.getId(), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_DELETED);
    }

    @Test
    void moderatorsDeleteOtherPeoplesMessagesOnlyWithAReason() {
        Conversation team = messaging.createLocked(ConversationKind.STORE_TEAM);
        messaging.syncMembers(team.getId(), Set.of(me, bob));
        ConversationMessage message = messaging.send(bob, team.getId(), "Spam", null);
        when(moderation.isModerator(any(SessionPrincipal.class), any(Conversation.class))).thenAnswer(
                i -> ((SessionPrincipal) i.getArgument(0)).userId().equals(me));
        assertThatThrownBy(() -> messaging.deleteMessage(principal(me), team.getId(), message.getId(), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.DELETE_REASON_REQUIRED);
        assertThatThrownBy(() -> messaging.deleteMessage(principal(me), team.getId(), message.getId(), "zu kurz"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.DELETE_REASON_REQUIRED);
        assertThatThrownBy(() -> messaging.editMessage(principal(me), team.getId(), message.getId(), "x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.MESSAGE_NOT_EDITABLE);
        assertThatThrownBy(() -> messaging.deleteMessage(principal(carol), team.getId(), message.getId(), null))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.NOT_CONVERSATION_MEMBER);

        ConversationMessage deleted = messaging.deleteMessage(principal(me), team.getId(), message.getId(),
                "Werbung gehört nicht in den Team-Chat");
        assertThat(deleted.isDeletedByModerator()).isTrue();
        assertThat(historyOf(team.getId(), "MESSAGE_DELETED")).singleElement().satisfies(entry -> {
            assertThat(entry.getOldValue()).isEqualTo("Spam");
            assertThat(entry.getReason()).isEqualTo("Werbung gehört nicht in den Team-Chat");
        });
    }
}
