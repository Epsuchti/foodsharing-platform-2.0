package network.foodsharing.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.config.AppProperties;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.repository.ForumFollowRepository;
import network.foodsharing.forum.repository.ForumThreadFollowRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** F5/A20 (formerly ForumFlowIT): a mention rings readers of the forum only, never the author, never who opted out. */
class ForumMentionBellsTest {

    static final UUID AUTHOR = UUID.randomUUID();
    static final UUID READER = UUID.randomUUID();
    static final UUID OPTED_OUT = UUID.randomUUID();
    static final UUID OUTSIDER = UUID.randomUUID();

    NotificationService notifications = mock(NotificationService.class);
    UserService users = mock(UserService.class);
    ForumNotifier notifier = new ForumNotifier(notifications, mock(MailOutboxService.class),
            mock(RealtimePublisher.class), mock(ForumThreadFollowRepository.class), mock(ForumFollowRepository.class),
            users, mock(AppProperties.class), Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));

    static User user(UUID id, boolean mentions) {
        User user = new User();
        user.setId(id);
        user.setMentionNotifications(mentions);
        return user;
    }

    @Test
    void mentionsRingReadersOnlyNeverTheAuthorNorWhoSwitchedThemOff() {
        ForumThread thread = new ForumThread();
        thread.setId(UUID.randomUUID());
        thread.setTitle("Suche Mitfahrgelegenheit");
        ForumPost post = new ForumPost();
        post.setId(UUID.randomUUID());
        post.setAuthorId(AUTHOR);
        when(users.findAll(any())).thenReturn(List.of(user(READER, true), user(OPTED_OUT, false)));

        notifier.mentioned(thread, post, "Jonas", Set.of(AUTHOR, READER, OPTED_OUT, OUTSIDER),
                Set.of(AUTHOR, READER, OPTED_OUT));

        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.FORUM_MENTION);
        assertThat(request.getValue().recipientIds()).containsExactly(READER);
    }

    @Test
    void noBellWhenOnlyTheAuthorIsMentioned() {
        ForumPost post = new ForumPost();
        post.setId(UUID.randomUUID());
        post.setAuthorId(AUTHOR);
        notifier.mentioned(new ForumThread(), post, "Jonas", Set.of(AUTHOR), Set.of(AUTHOR));
        verify(notifications, never()).notify(any());
    }
}
