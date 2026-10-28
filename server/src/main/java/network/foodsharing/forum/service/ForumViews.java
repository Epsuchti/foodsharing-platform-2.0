package network.foodsharing.forum.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumPostReaction;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ForumThreadFollow;
import org.springframework.data.domain.Page;

/** Read models handed to the forum mapper. */
public final class ForumViews {

    private ForumViews() {
    }

    /** A thread page with the actor's thread follows by thread id. */
    public record ThreadListing(Page<ForumThread> page, Map<UUID, ForumThreadFollow> follows) {
    }

    /** A thread with its visible posts, their reactions and the actor's facts and follow (null = none). */
    public record ThreadDetails(ForumThread thread, List<ForumPost> posts, List<ForumPostReaction> reactions,
                                ForumThreadFollow follow, ForumRules.Actor actor, ForumRules.Forum forum) {
    }

    /** One post with its reactions and the actor's facts. */
    public record PostDetails(ForumThread thread, ForumPost post, List<ForumPostReaction> reactions,
                              ForumRules.Actor actor, ForumRules.Forum forum) {
    }

    /** Search hit: the thread, an optional snippet and the actor's follow. */
    public record SearchHit(ForumThread thread, String snippet, ForumThreadFollow follow) {
    }

    public record SearchResult(Page<ForumThread> page, List<SearchHit> hits) {
    }
}
