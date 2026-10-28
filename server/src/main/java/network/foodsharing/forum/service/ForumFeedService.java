package network.foodsharing.forum.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.repository.ForumPostRepository;
import network.foodsharing.region.service.RegionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forum posts for the dashboard activity feed (A19): visible posts of active threads in the forums of the user's
 * member regions/groups and the ambassador forums of the regions the user administrates ({@link ForumRules#mayAccess}).
 */
@Service
@Transactional(readOnly = true)
public class ForumFeedService {

    /** One feed post (hidden and deleted posts never appear). */
    public record FeedPost(UUID id, UUID threadId, String threadTitle, UUID regionId, UUID authorId, String body,
                           Instant createdAt) {
    }

    /** Placeholder so that {@code in} lists are never empty. */
    private static final UUID NONE = new UUID(0L, 0L);

    private final ForumPostRepository posts;
    private final RegionService regions;

    public ForumFeedService(ForumPostRepository posts, RegionService regions) {
        this.posts = posts;
        this.regions = regions;
    }

    public List<FeedPost> latest(UUID userId, Instant before, int limit) {
        Set<UUID> members = new HashSet<>(regions.activeRegionIdsOf(userId));
        Set<UUID> admins = new HashSet<>(regions.adminRegionIds(userId));
        if (members.isEmpty() && admins.isEmpty()) {
            return List.of();
        }
        members.add(NONE);
        admins.add(NONE);
        return posts.feed(members, admins, before, PageRequest.of(0, Math.max(limit, 1))).stream().map(row -> {
            ForumPost p = (ForumPost) row[0];
            ForumThread t = (ForumThread) row[1];
            return new FeedPost(p.getId(), t.getId(), t.getTitle(), t.getRegionId(), p.getAuthorId(), p.getBody(),
                    p.getCreatedAt());
        }).toList();
    }
}
