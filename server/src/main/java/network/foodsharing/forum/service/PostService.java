package network.foodsharing.forum.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumAction;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ThreadStatus;
import network.foodsharing.forum.repository.ForumPostReactionRepository;
import network.foodsharing.forum.repository.ForumPostRepository;
import network.foodsharing.forum.service.ForumPermissionService.ThreadContext;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forum posts (F3, F4): reply, edit with edit marker, delete, hide/unhide with reason, mentions (A36, A37).
 */
@Service
public class PostService {

    private final ForumPostRepository posts;
    private final ForumPostReactionRepository reactions;
    private final ForumPermissionService access;
    private final ForumNotifier notifier;
    private final ThreadService threads;
    private final HistoryService history;
    private final Clock clock;

    public PostService(ForumPostRepository posts, ForumPostReactionRepository reactions,
                       ForumPermissionService access, ForumNotifier notifier, ThreadService threads,
                       HistoryService history, Clock clock) {
        this.posts = posts;
        this.reactions = reactions;
        this.access = access;
        this.notifier = notifier;
        this.threads = threads;
        this.history = history;
        this.clock = clock;
    }

    /** @throws ApiException 404 THREAD_NOT_FOUND, 409 THREAD_CLOSED / THREAD_NOT_ACTIVE, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional
    public ForumViews.PostDetails create(UUID threadId, String body) {
        ThreadContext context = access.visibleThread(threadId);
        ForumThread thread = context.thread();
        if (!ForumRules.mayPostToThread(context.actor(), context.forum(), thread.getStatus(), thread.isActive())) {
            throw ApiException.conflict(thread.getStatus() == ThreadStatus.CLOSED ? ApiErrorCode.THREAD_CLOSED
                    : ApiErrorCode.THREAD_NOT_ACTIVE);
        }
        Instant now = clock.instant();
        ForumPost post = new ForumPost();
        post.setThreadId(thread.getId());
        post.setAuthorId(context.actor().userId());
        post.setBody(body);
        post = posts.save(post);
        thread.setPostCount(thread.getPostCount() + 1);
        thread.setLastPostAt(now);
        thread.setLastPostAuthorId(post.getAuthorId());
        history.record(event(ForumAction.POST_CREATED, thread, post).build());
        if (thread.isActive()) {
            notifier.postCreated(thread, post, threads.displayName(post.getAuthorId()), access.withAccess(
                    ForumNotifier.mentionedUserIds(body), thread.getRegionId(), context.forum()));
        }
        return details(context, post);
    }

    /** Authors edit their posts; the post gets an edit marker. @throws ApiException 404 POST_NOT_FOUND, 403 */
    @Transactional
    public ForumViews.PostDetails edit(UUID postId, String body) {
        ForumPost post = require(postId);
        ThreadContext context = access.visibleThread(post.getThreadId());
        ForumPermissionService.require(ForumRules.mayEditPost(context.actor(), context.forum(), post.getAuthorId()));
        if (!body.equals(post.getBody())) {
            post.setBody(body);
            post.setEditedAt(clock.instant());
            post.setEditedBy(context.actor().userId());
            history.record(event(ForumAction.POST_EDITED, context.thread(), post).build());
            notifier.publish(context.thread(), post.getId(), "EDITED", Set.of());
        }
        return details(context, post);
    }

    /**
     * Soft delete by the author or ORGA (upstream); a thread without posts is deleted too.
     *
     * @throws ApiException 404 POST_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional
    public void delete(UUID postId) {
        ForumPost post = require(postId);
        ThreadContext context = access.visibleThread(post.getThreadId());
        ForumThread thread = context.thread();
        ForumPermissionService.require(ForumRules.mayDeletePost(context.actor(), post.getAuthorId()));
        post.setDeletedAt(clock.instant());
        posts.flush();
        history.record(event(ForumAction.POST_DELETED, thread, post).build());
        notifier.mentionsRemoved(post.getId());
        notifier.postUnhidden(post.getId());
        recount(thread);
        if (thread.getPostCount() == 0) {
            thread.setDeletedAt(clock.instant());
            history.record(history.event(ForumAction.THREAD_DELETED, thread.getId()).region(thread.getRegionId())
                    .metadata("title", thread.getTitle()).build());
            notifier.threadRemoved(thread.getId());
            return;
        }
        notifier.publish(thread, post.getId(), "DELETED", Set.of());
    }

    /**
     * Moderators hide a post with a reason when the region allows it; the author gets {@code POST_HIDDEN}. When every
     * post is hidden the whole thread becomes inactive (upstream "hide thread").
     *
     * @throws ApiException 404 POST_NOT_FOUND, 403 ACCESS_DENIED / HIDING_NOT_ALLOWED, 409 POST_ALREADY_HIDDEN
     */
    @Transactional
    public ForumViews.PostDetails hide(UUID postId, String reason) {
        ForumPost post = require(postId);
        ThreadContext context = access.visibleThread(post.getThreadId());
        ForumThread thread = context.thread();
        ForumPermissionService.require(ForumRules.mayModerate(context.actor(), context.forum()));
        if (!ForumRules.mayHidePosts(context.actor(), context.forum())) {
            throw ApiException.forbidden(ApiErrorCode.HIDING_NOT_ALLOWED);
        }
        if (post.isHidden()) {
            throw ApiException.conflict(ApiErrorCode.POST_ALREADY_HIDDEN);
        }
        post.setHiddenAt(clock.instant());
        post.setHiddenBy(context.actor().userId());
        post.setHiddenReason(reason.strip());
        posts.flush();
        history.record(event(ForumAction.POST_HIDDEN, thread, post).reason(post.getHiddenReason()).build());
        if (!post.getAuthorId().equals(context.actor().userId())) {
            notifier.postHidden(thread, post, post.getHiddenReason());
        }
        if (thread.isActive() && posts.countByThreadIdAndDeletedAtIsNullAndHiddenAtIsNull(thread.getId()) == 0) {
            thread.setActive(false);
        }
        notifier.publish(thread, post.getId(), "HIDDEN", Set.of());
        return details(context, post);
    }

    /** @throws ApiException 404 POST_NOT_FOUND, 403 ACCESS_DENIED, 409 POST_NOT_HIDDEN */
    @Transactional
    public ForumViews.PostDetails unhide(UUID postId) {
        ForumPost post = require(postId);
        ThreadContext context = access.visibleThread(post.getThreadId());
        ForumThread thread = context.thread();
        ForumPermissionService.require(ForumRules.mayUnhidePosts(context.actor(), context.forum()));
        if (!post.isHidden()) {
            throw ApiException.conflict(ApiErrorCode.POST_NOT_HIDDEN);
        }
        boolean allHidden = posts.countByThreadIdAndDeletedAtIsNullAndHiddenAtIsNull(thread.getId()) == 0;
        String reason = post.getHiddenReason();
        post.setHiddenAt(null);
        post.setHiddenBy(null);
        post.setHiddenReason(null);
        history.record(event(ForumAction.POST_UNHIDDEN, thread, post).metadata("previousReason", reason).build());
        notifier.postUnhidden(post.getId());
        if (allHidden && !thread.isActive()) {
            thread.setActive(true);
        }
        notifier.publish(thread, post.getId(), "UNHIDDEN", Set.of());
        return details(context, post);
    }

    ForumPost require(UUID postId) {
        return posts.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.POST_NOT_FOUND));
    }

    ForumViews.PostDetails details(ThreadContext context, ForumPost post) {
        return new ForumViews.PostDetails(context.thread(), post,
                reactions.findByPostIdInOrderByCreatedAtAsc(List.of(post.getId())), context.actor(), context.forum());
    }

    private void recount(ForumThread thread) {
        thread.setPostCount((int) posts.countByThreadIdAndDeletedAtIsNull(thread.getId()));
        posts.findFirstByThreadIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(thread.getId()).ifPresent(last -> {
            thread.setLastPostAt(last.getCreatedAt());
            thread.setLastPostAuthorId(last.getAuthorId());
        });
    }

    private HistoryEvent.Builder event(ForumAction action, ForumThread thread, ForumPost post) {
        return history.event(action, thread.getId()).region(thread.getRegionId())
                .metadata("postId", post.getId().toString());
    }
}
