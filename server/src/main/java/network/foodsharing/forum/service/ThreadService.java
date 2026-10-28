package network.foodsharing.forum.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumAction;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumPostReaction;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ForumThreadFollow;
import network.foodsharing.forum.domain.ThreadStatus;
import network.foodsharing.forum.repository.ForumPostReactionRepository;
import network.foodsharing.forum.repository.ForumPostRepository;
import network.foodsharing.forum.repository.ForumThreadFollowRepository;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.forum.service.ForumPermissionService.ForumContext;
import network.foodsharing.forum.service.ForumPermissionService.ThreadContext;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forum threads (F1, F2, G3): listing, moderated activation flow, rename, close/open, sticky, delete (A36).
 */
@Service
public class ThreadService {

    /** Requested thread changes; null = unchanged. */
    public record ThreadChange(String title, ThreadStatus status, Boolean sticky, Boolean active) {
    }

    private final ForumThreadRepository threads;
    private final ForumPostRepository posts;
    private final ForumPostReactionRepository reactions;
    private final ForumThreadFollowRepository follows;
    private final ForumPermissionService access;
    private final ForumNotifier notifier;
    private final HistoryService history;
    private final UserService users;
    private final Clock clock;

    public ThreadService(ForumThreadRepository threads, ForumPostRepository posts,
                         ForumPostReactionRepository reactions, ForumThreadFollowRepository follows,
                         ForumPermissionService access, ForumNotifier notifier, HistoryService history,
                         UserService users, Clock clock) {
        this.threads = threads;
        this.posts = posts;
        this.reactions = reactions;
        this.follows = follows;
        this.access = access;
        this.notifier = notifier;
        this.history = history;
        this.users = users;
        this.clock = clock;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional(readOnly = true)
    public ForumViews.ThreadListing list(UUID regionId, boolean ambassadorForum, int page, int size) {
        ForumContext forum = access.accessibleForum(regionId, ambassadorForum);
        boolean moderator = ForumRules.mayModerate(forum.actor(), forum.forum());
        Page<ForumThread> result = threads.list(regionId, ambassadorForum, moderator, forum.actor().userId(),
                PageRequest.of(Math.max(0, page), clamp(size)));
        return new ForumViews.ThreadListing(result, followsOf(forum.actor().userId(), result.getContent()));
    }

    /** @throws ApiException 404 THREAD_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional(readOnly = true)
    public ForumViews.ThreadDetails get(UUID threadId) {
        return details(access.visibleThread(threadId));
    }

    /**
     * Starts a thread with its first post. Active at once when {@link ForumRules#mayStartUnmoderatedThread};
     * otherwise moderators get {@code FORUM_THREAD_TO_ACTIVATE}.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE
     */
    @Transactional
    public ForumViews.ThreadDetails create(UUID regionId, boolean ambassadorForum, String title, String body,
                                           boolean followBell, boolean followMail) {
        ForumContext forum = access.accessibleForum(regionId, ambassadorForum);
        ForumPermissionService.require(ForumRules.mayStartThread(forum.actor(), forum.forum()));
        Instant now = clock.instant();
        UUID authorId = forum.actor().userId();
        ForumThread thread = new ForumThread();
        thread.setRegionId(regionId);
        thread.setAmbassadorForum(ambassadorForum);
        thread.setTitle(title.strip());
        thread.setAuthorId(authorId);
        thread.setStatus(ThreadStatus.OPEN);
        thread.setActive(ForumRules.mayStartUnmoderatedThread(forum.actor(), forum.forum()));
        thread.setPostCount(1);
        thread.setLastPostAt(now);
        thread.setLastPostAuthorId(authorId);
        thread = threads.save(thread);
        ForumPost post = new ForumPost();
        post.setThreadId(thread.getId());
        post.setAuthorId(authorId);
        post.setBody(body);
        posts.save(post);
        if (followBell || followMail) {
            ForumThreadFollow follow = new ForumThreadFollow();
            follow.setThreadId(thread.getId());
            follow.setUserId(authorId);
            follow.setBell(followBell);
            follow.setMail(followMail);
            follows.save(follow);
        }
        history.record(event(ForumAction.THREAD_CREATED, thread).metadata("postId", post.getId().toString())
                .metadata("active", thread.isActive()).metadata("title", thread.getTitle()).build());
        String authorName = displayName(authorId);
        if (thread.isActive()) {
            notifier.threadActive(thread, authorName);
            notifier.postCreated(thread, post, authorName,
                    access.withAccess(ForumNotifier.mentionedUserIds(body), regionId, forum.forum()));
        } else {
            notifier.threadToActivate(thread, access.moderators(regionId), authorName);
        }
        return details(new ThreadContext(thread, forum.actor(), forum.forum()));
    }

    /**
     * Rename (moderator or author), close/open and sticky (moderator), activate (moderator; active=false is not
     * supported).
     *
     * @throws ApiException 404 THREAD_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_FAILED
     */
    @Transactional
    public ForumViews.ThreadDetails update(UUID threadId, ThreadChange change) {
        ThreadContext context = access.visibleThread(threadId);
        ForumThread thread = context.thread();
        ForumRules.Actor actor = context.actor();
        ForumRules.Forum forum = context.forum();
        if (change.title() != null && !change.title().strip().equals(thread.getTitle())) {
            ForumPermissionService.require(ForumRules.mayRename(actor, forum, thread.getAuthorId()));
            String old = thread.getTitle();
            thread.setTitle(change.title().strip());
            history.record(event(ForumAction.THREAD_RENAMED, thread).change("title", old, thread.getTitle()).build());
        }
        if (change.status() != null && change.status() != thread.getStatus()) {
            ForumPermissionService.require(ForumRules.mayChangeStatus(actor, forum));
            thread.setStatus(change.status());
            history.record(event(change.status() == ThreadStatus.CLOSED ? ForumAction.THREAD_CLOSED
                    : ForumAction.THREAD_OPENED, thread).build());
        }
        if (change.sticky() != null && change.sticky() != thread.isSticky()) {
            ForumPermissionService.require(ForumRules.mayChangeSticky(actor, forum));
            thread.setSticky(change.sticky());
            history.record(event(ForumAction.THREAD_STICKY_CHANGED, thread)
                    .change("sticky", !change.sticky(), change.sticky()).build());
        }
        if (change.active() != null && change.active() != thread.isActive()) {
            if (!change.active()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            ForumPermissionService.require(ForumRules.mayModerate(actor, forum));
            thread.setActive(true);
            history.record(event(ForumAction.THREAD_ACTIVATED, thread).build());
            notifier.threadActive(thread, displayName(thread.getAuthorId()));
        }
        notifier.publish(thread, null, "THREAD", Set.of());
        return details(context);
    }

    /**
     * Soft delete: moderators delete inactive threads, ORGA any thread; bells of the thread go away.
     *
     * @throws ApiException 404 THREAD_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional
    public void delete(UUID threadId) {
        ThreadContext context = access.visibleThread(threadId);
        ForumThread thread = context.thread();
        ForumPermissionService.require(ForumRules.mayDeleteThread(context.actor(), context.forum(), thread.isActive()));
        thread.setDeletedAt(clock.instant());
        history.record(event(ForumAction.THREAD_DELETED, thread).metadata("title", thread.getTitle()).build());
        notifier.threadRemoved(thread.getId());
    }

    ForumViews.ThreadDetails details(ThreadContext context) {
        ForumThread thread = context.thread();
        List<ForumPost> visible = posts.findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(thread.getId());
        List<ForumPostReaction> postReactions = visible.isEmpty() ? List.of()
                : reactions.findByPostIdInOrderByCreatedAtAsc(visible.stream().map(ForumPost::getId).toList());
        ForumThreadFollow follow = follows.findByThreadIdAndUserId(thread.getId(), context.actor().userId())
                .orElse(null);
        return new ForumViews.ThreadDetails(thread, visible, postReactions, follow, context.actor(), context.forum());
    }

    Map<UUID, ForumThreadFollow> followsOf(UUID userId, List<ForumThread> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return follows.findByUserIdAndThreadIdIn(userId, list.stream().map(ForumThread::getId).toList()).stream()
                .collect(Collectors.toMap(ForumThreadFollow::getThreadId, Function.identity()));
    }

    String displayName(UUID userId) {
        return users.findActive(userId).map(User::getDisplayName).orElse("");
    }

    private HistoryEvent.Builder event(ForumAction action, ForumThread thread) {
        return history.event(action, thread.getId()).region(thread.getRegionId());
    }

    static int clamp(int size) {
        return size <= 0 ? 20 : Math.min(size, 100);
    }
}
