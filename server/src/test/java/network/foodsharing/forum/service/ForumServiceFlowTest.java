package network.foodsharing.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ThreadStatus;
import network.foodsharing.forum.repository.ForumPostReactionRepository;
import network.foodsharing.forum.repository.ForumPostRepository;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.forum.service.ForumPermissionService.ForumContext;
import network.foodsharing.forum.service.ForumPermissionService.ThreadContext;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

/**
 * F1–F5/G3 service paths without a database (formerly ForumFlowIT): posting into a closed or not yet activated thread
 * is refused with its own code, a post is hidden only once, and the forum search never asks the repositories for
 * hidden post texts on behalf of a member (only moderators get them); searching every forum is ORGA's.
 */
class ForumServiceFlowTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID ME = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();
    static final ForumRules.Actor MEMBER = new ForumRules.Actor(ME, false, true, false, false, true);
    static final ForumRules.Actor MODERATOR = new ForumRules.Actor(ME, false, true, true, true, true);
    static final ForumRules.Forum MODERATED = new ForumRules.Forum(false, true, true);

    ForumPostRepository posts = mock(ForumPostRepository.class);
    ForumThreadRepository threads = mock(ForumThreadRepository.class);
    ForumPermissionService access = mock(ForumPermissionService.class);
    ThreadService threadService = mock(ThreadService.class);
    PostService postService = new PostService(posts, mock(ForumPostReactionRepository.class), access,
            mock(ForumNotifier.class), threadService, mock(HistoryService.class), Clock.fixed(NOW, ZoneOffset.UTC));

    static ForumThread thread(ThreadStatus status, boolean active) {
        ForumThread thread = new ForumThread();
        thread.setId(UUID.randomUUID());
        thread.setRegionId(REGION);
        thread.setTitle("Kühlschrank");
        thread.setStatus(status);
        thread.setActive(active);
        return thread;
    }

    private static ApiErrorCode code(Throwable error) {
        return ((ApiException) error).getCode();
    }

    @Test
    void membersCannotPostIntoClosedOrInactiveThreads() {
        ForumThread closed = thread(ThreadStatus.CLOSED, true);
        when(access.visibleThread(closed.getId())).thenReturn(new ThreadContext(closed, MEMBER, MODERATED));
        assertThatThrownBy(() -> postService.create(closed.getId(), "Noch Platz?"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.THREAD_CLOSED));

        ForumThread inactive = thread(ThreadStatus.OPEN, false);
        when(access.visibleThread(inactive.getId())).thenReturn(new ThreadContext(inactive, MEMBER, MODERATED));
        assertThatThrownBy(() -> postService.create(inactive.getId(), "x"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.THREAD_NOT_ACTIVE));
        verify(posts, never()).save(any());
    }

    @Test
    void aHiddenPostCannotBeHiddenAgainAndMembersCannotHide() {
        ForumThread open = thread(ThreadStatus.OPEN, true);
        ForumPost post = new ForumPost();
        post.setId(UUID.randomUUID());
        post.setThreadId(open.getId());
        when(posts.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));

        when(access.visibleThread(open.getId())).thenReturn(new ThreadContext(open, MEMBER, MODERATED));
        assertThatThrownBy(() -> postService.hide(post.getId(), "Off-topic")).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.ACCESS_DENIED));

        post.setHiddenAt(NOW.minusSeconds(60));
        when(access.visibleThread(open.getId())).thenReturn(new ThreadContext(open, MODERATOR, MODERATED));
        assertThatThrownBy(() -> postService.hide(post.getId(), "again"))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.POST_ALREADY_HIDDEN));
    }

    private ForumSearchService search(PermissionService permissions, CurrentActorService actors) {
        return new ForumSearchService(threads, posts, access, threadService, actors, permissions);
    }

    @Test
    void membersSearchWithoutHiddenTextsModeratorsWithThem() {
        ForumThread hit = thread(ThreadStatus.OPEN, true);
        hit.setTitle("Technikfragen");
        when(threads.search(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of(hit)));
        when(threadService.followsOf(any(), any())).thenReturn(Map.of());
        when(posts.matching(any(), anyString(), anyBoolean())).thenReturn(List.of());
        ForumSearchService service = search(mock(PermissionService.class), mock(CurrentActorService.class));

        when(access.accessibleForum(REGION, false)).thenReturn(new ForumContext(REGION, MEMBER, MODERATED));
        service.searchRegion(REGION, false, "idioten", true, 0, 20);
        verify(threads).search(eq("%idioten%"), eq(true), eq(false), eq(false), eq(REGION), eq(false), any());
        verify(posts).matching(hit.getId(), "%idioten%", false);

        when(access.accessibleForum(REGION, false)).thenReturn(new ForumContext(REGION, MODERATOR, MODERATED));
        service.searchRegion(REGION, false, "idioten", true, 0, 20);
        verify(threads).search(eq("%idioten%"), eq(true), eq(true), eq(false), eq(REGION), eq(false), any());
        verify(posts).matching(hit.getId(), "%idioten%", true);
    }

    @Test
    void onlyOrgaSearchesEveryForum() {
        PermissionService permissions = mock(PermissionService.class);
        CurrentActorService actors = mock(CurrentActorService.class);
        SessionPrincipal principal = TestAuth.principal(SeedUsers.FOODSAVER);
        when(actors.requirePrincipal()).thenReturn(principal);
        when(permissions.isOrga(principal)).thenReturn(false);
        assertThatThrownBy(() -> search(permissions, actors).searchAll("brot", false, 0, 20))
                .satisfies(e -> assertThat(code(e)).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        verify(threads, never()).search(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any(), anyBoolean(),
                any());
    }
}
