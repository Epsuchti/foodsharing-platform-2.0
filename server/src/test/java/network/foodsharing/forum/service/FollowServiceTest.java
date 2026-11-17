package network.foodsharing.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ForumThreadFollow;
import network.foodsharing.forum.repository.ForumFollowRepository;
import network.foodsharing.forum.repository.ForumThreadFollowRepository;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.forum.service.FollowService.ThreadFollowState;
import network.foodsharing.forum.service.ForumPermissionService.ThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * F5 thread follows: the call carries the full state (bell, mail; both false unfollows), and concurrent calls of one
 * user are serialised by a row lock on the thread taken before the follow row is read, so quick toggles from two tabs
 * neither insert the row twice nor overwrite each other with a stale read.
 */
class FollowServiceTest {

    static final UUID ME = UUID.randomUUID();
    static final ForumRules.Actor MEMBER = new ForumRules.Actor(ME, false, true, false, false, true);
    static final ForumRules.Forum FORUM = new ForumRules.Forum(false, true, true);

    ForumThreadFollowRepository threadFollows = mock(ForumThreadFollowRepository.class);
    ForumThreadRepository threads = mock(ForumThreadRepository.class);
    ForumPermissionService access = mock(ForumPermissionService.class);
    FollowService service = new FollowService(threadFollows, mock(ForumFollowRepository.class), threads, access);
    ForumThread thread = new ForumThread();

    @BeforeEach
    void visibleThread() {
        thread.setId(UUID.randomUUID());
        when(access.visibleThread(thread.getId())).thenReturn(new ThreadContext(thread, MEMBER, FORUM));
        when(threads.findForUpdate(thread.getId())).thenReturn(Optional.of(thread));
    }

    private ForumThreadFollow existing(boolean bell, boolean mail) {
        ForumThreadFollow follow = new ForumThreadFollow();
        follow.setThreadId(thread.getId());
        follow.setUserId(ME);
        follow.setBell(bell);
        follow.setMail(mail);
        when(threadFollows.findByThreadIdAndUserId(thread.getId(), ME)).thenReturn(Optional.of(follow));
        return follow;
    }

    @Test
    void locksTheThreadBeforeReadingTheFollowRow() {
        when(threadFollows.findByThreadIdAndUserId(thread.getId(), ME)).thenReturn(Optional.empty());

        service.followThread(thread.getId(), true, false);

        InOrder order = inOrder(threads, threadFollows);
        order.verify(threads).findForUpdate(thread.getId());
        order.verify(threadFollows).findByThreadIdAndUserId(thread.getId(), ME);
        order.verify(threadFollows).save(any());
    }

    @Test
    void firstFollowInsertsTheRowWithBothFlags() {
        when(threadFollows.findByThreadIdAndUserId(thread.getId(), ME)).thenReturn(Optional.empty());

        assertThat(service.followThread(thread.getId(), true, false)).isEqualTo(new ThreadFollowState(true, false));

        ArgumentCaptor<ForumThreadFollow> saved = ArgumentCaptor.forClass(ForumThreadFollow.class);
        verify(threadFollows).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(ME);
        assertThat(saved.getValue().isBell()).isTrue();
        assertThat(saved.getValue().isMail()).isFalse();
    }

    @Test
    void laterCallsReplaceTheFullStateOfTheExistingRow() {
        ForumThreadFollow follow = existing(true, false);

        assertThat(service.followThread(thread.getId(), false, true)).isEqualTo(new ThreadFollowState(false, true));

        verify(threadFollows).save(follow);
        assertThat(follow.isBell()).isFalse();
        assertThat(follow.isMail()).isTrue();
    }

    @Test
    void bothOffUnfollows() {
        ForumThreadFollow follow = existing(true, true);

        assertThat(service.followThread(thread.getId(), false, false)).isEqualTo(new ThreadFollowState(false, false));

        verify(threadFollows).delete(follow);
        verify(threadFollows, never()).save(any());
    }
}
