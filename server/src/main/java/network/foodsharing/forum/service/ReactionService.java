package network.foodsharing.forum.service;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumPostReaction;
import network.foodsharing.forum.domain.ReactionKey;
import network.foodsharing.forum.repository.ForumPostReactionRepository;
import network.foodsharing.forum.service.ForumPermissionService.ThreadContext;
import network.foodsharing.history.NoHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emoji reactions on posts (F3, upstream fs_post_reaction): idempotent add/remove by everyone who may read. */
@Service
public class ReactionService {

    private final ForumPostReactionRepository reactions;
    private final PostService posts;
    private final ForumPermissionService access;
    private final ForumNotifier notifier;

    public ReactionService(ForumPostReactionRepository reactions, PostService posts, ForumPermissionService access,
                           ForumNotifier notifier) {
        this.reactions = reactions;
        this.posts = posts;
        this.access = access;
        this.notifier = notifier;
    }

    /** @throws ApiException 404 POST_NOT_FOUND, 400 REACTION_UNKNOWN, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional
    @NoHistory(reason = "reactions are lightweight feedback, visible on the post itself")
    public void add(UUID postId, ReactionKey key) {
        ForumPost post = posts.require(postId);
        ThreadContext context = access.visibleThread(post.getThreadId());
        requireKnown(key);
        UUID userId = context.actor().userId();
        if (reactions.findByPostIdAndUserIdAndReactionKey(postId, userId, key).isEmpty()) {
            ForumPostReaction reaction = new ForumPostReaction();
            reaction.setPostId(postId);
            reaction.setUserId(userId);
            reaction.setReactionKey(key);
            reactions.save(reaction);
            notifier.publish(context.thread(), postId, "REACTION", Set.of());
        }
    }

    /** @throws ApiException 404 POST_NOT_FOUND, 400 REACTION_UNKNOWN, 403 FORUM_NOT_ACCESSIBLE */
    @Transactional
    @NoHistory(reason = "reactions are lightweight feedback, visible on the post itself")
    public void remove(UUID postId, ReactionKey key) {
        ForumPost post = posts.require(postId);
        ThreadContext context = access.visibleThread(post.getThreadId());
        requireKnown(key);
        reactions.findByPostIdAndUserIdAndReactionKey(postId, context.actor().userId(), key).ifPresent(reaction -> {
            reactions.delete(reaction);
            notifier.publish(context.thread(), postId, "REACTION", Set.of());
        });
    }

    private static void requireKnown(ReactionKey key) {
        if (key == null) {
            throw ApiException.badRequest(ApiErrorCode.REACTION_UNKNOWN);
        }
    }
}
