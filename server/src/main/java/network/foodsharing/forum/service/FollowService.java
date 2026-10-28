package network.foodsharing.forum.service;

import java.util.UUID;
import network.foodsharing.forum.domain.ForumFollow;
import network.foodsharing.forum.domain.ForumThreadFollow;
import network.foodsharing.forum.repository.ForumFollowRepository;
import network.foodsharing.forum.repository.ForumThreadFollowRepository;
import network.foodsharing.forum.service.ForumPermissionService.ForumContext;
import network.foodsharing.forum.service.ForumPermissionService.ThreadContext;
import network.foodsharing.history.NoHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Following threads (bell and/or mail) and whole forums (F5). */
@Service
public class FollowService {

    /** Follow flags of a thread; both false = not following. */
    public record ThreadFollowState(boolean bell, boolean mail) {
    }

    private final ForumThreadFollowRepository threadFollows;
    private final ForumFollowRepository forumFollows;
    private final ForumPermissionService access;

    public FollowService(ForumThreadFollowRepository threadFollows, ForumFollowRepository forumFollows,
                         ForumPermissionService access) {
        this.threadFollows = threadFollows;
        this.forumFollows = forumFollows;
        this.access = access;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 THREAD_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional
    @NoHistory(reason = "personal notification preference")
    public ThreadFollowState followThread(UUID threadId, boolean bell, boolean mail) {
        ThreadContext context = access.visibleThread(threadId);
        UUID userId = context.actor().userId();
        ForumThreadFollow follow = threadFollows.findByThreadIdAndUserId(threadId, userId).orElse(null);
        if (!bell && !mail) {
            if (follow != null) {
                threadFollows.delete(follow);
            }
            return new ThreadFollowState(false, false);
        }
        if (follow == null) {
            follow = new ForumThreadFollow();
            follow.setThreadId(threadId);
            follow.setUserId(userId);
        }
        follow.setBell(bell);
        follow.setMail(mail);
        threadFollows.save(follow);
        return new ThreadFollowState(bell, mail);
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional(readOnly = true)
    public boolean isFollowingForum(UUID regionId, boolean ambassadorForum) {
        ForumContext context = access.accessibleForum(regionId, ambassadorForum);
        return forumFollows.findByRegionIdAndAmbassadorForumAndUserId(regionId, ambassadorForum,
                context.actor().userId()).isPresent();
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional
    @NoHistory(reason = "personal notification preference")
    public boolean setForumFollow(UUID regionId, boolean ambassadorForum, boolean following) {
        ForumContext context = access.accessibleForum(regionId, ambassadorForum);
        UUID userId = context.actor().userId();
        var existing = forumFollows.findByRegionIdAndAmbassadorForumAndUserId(regionId, ambassadorForum, userId);
        if (following && existing.isEmpty()) {
            ForumFollow follow = new ForumFollow();
            follow.setRegionId(regionId);
            follow.setAmbassadorForum(ambassadorForum);
            follow.setUserId(userId);
            forumFollows.save(follow);
        } else if (!following) {
            existing.ifPresent(forumFollows::delete);
        }
        return following;
    }
}
