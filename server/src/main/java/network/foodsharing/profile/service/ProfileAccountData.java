package network.foodsharing.profile.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.profile.domain.UserWallPost;
import network.foodsharing.profile.repository.BuddyRepository;
import network.foodsharing.profile.repository.RatingRepository;
import network.foodsharing.profile.repository.UserWallPostRepository;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion and GDPR export for bananas, buddies and the profile wall (A40). Deleting removes bananas and
 * buddy rows in both directions and the posts on the user's own wall; posts on other walls stay attributed to the
 * anonymised account (upstream behaviour).
 */
@Component
public class ProfileAccountData implements AccountDeletionContributor, DataExportContributor {

    private final RatingRepository ratings;
    private final BuddyRepository buddies;
    private final UserWallPostRepository posts;

    public ProfileAccountData(RatingRepository ratings, BuddyRepository buddies, UserWallPostRepository posts) {
        this.ratings = ratings;
        this.buddies = buddies;
        this.posts = posts;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        ratings.deleteByRecipientIdOrSenderId(userId, userId);
        buddies.deleteByUserIdOrBuddyId(userId, userId);
        posts.deleteAll(posts.findByUserIdOrAuthorId(userId, userId).stream()
                .filter(post -> post.getUserId().equals(userId)).toList());
    }

    @Override
    public String section() {
        return "community";
    }

    @Override
    public Object export(UUID userId) {
        List<Map<String, Object>> received = ratings.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> Map.<String, Object>of("senderId", r.getSenderId(), "message", r.getMessage(),
                        "givenAt", r.getCreatedAt()))
                .toList();
        List<Map<String, Object>> sent = ratings.findBySenderIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> Map.<String, Object>of("recipientId", r.getRecipientId(), "message", r.getMessage(),
                        "givenAt", r.getCreatedAt()))
                .toList();
        List<Map<String, Object>> buddyRows = buddies.findByUserIdAndStatus(userId,
                        network.foodsharing.user.domain.BuddyStatus.CONFIRMED).stream()
                .map(b -> Map.<String, Object>of("buddyId", b.getBuddyId(), "since", b.getCreatedAt())).toList();
        List<Map<String, Object>> wall = posts.findByUserIdOrAuthorId(userId, userId).stream()
                .filter(post -> post.getDeletedAt() == null)
                .map(ProfileAccountData::post).toList();
        return Map.of("bananasReceived", received, "bananasSent", sent, "buddies", buddyRows, "wallPosts", wall);
    }

    private static Map<String, Object> post(UserWallPost post) {
        Instant at = post.getCreatedAt();
        return Map.of("wallOf", post.getUserId(), "authorId", post.getAuthorId(), "body", post.getBody(),
                "createdAt", at);
    }
}
