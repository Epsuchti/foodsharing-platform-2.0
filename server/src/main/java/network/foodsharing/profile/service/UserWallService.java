package network.foodsharing.profile.service;

import java.time.Clock;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.profile.domain.UserWallPost;
import network.foodsharing.profile.repository.UserWallPostRepository;
import network.foodsharing.user.domain.UserAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile wall (A42, upstream {@code WallPostPermissions} PROFILE): every signed-in user reads, the owner writes
 * (status updates), ORGA / author / owner / the owner's administrators delete.
 */
@Service
public class UserWallService {

    /** A post with the delete right of the current viewer. */
    public record PostView(UserWallPost post, boolean mayDelete) {
    }

    private final UserWallPostRepository posts;
    private final ProfilePermissionService access;
    private final HistoryService history;
    private final Clock clock;

    public UserWallService(UserWallPostRepository posts, ProfilePermissionService access, HistoryService history,
                           Clock clock) {
        this.posts = posts;
        this.access = access;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PostView> list(UUID userId, int page, int size) {
        ProfilePermissionService.Context context = access.basic(userId);
        return posts.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size))
                .map(post -> new PostView(post, mayDelete(context, post)));
    }

    /** @throws ApiException 404 USER_NOT_FOUND, 403 WALL_NOT_ACCESSIBLE, 400 VALIDATION_FAILED */
    @Transactional
    public PostView create(UUID userId, String body) {
        ProfilePermissionService.Context context = access.basic(userId);
        if (!context.permissions().mayPostWall()) {
            throw ApiException.forbidden(ApiErrorCode.WALL_NOT_ACCESSIBLE);
        }
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        UserWallPost post = new UserWallPost();
        post.setUserId(userId);
        post.setAuthorId(context.actor().userId());
        post.setBody(body.strip());
        post = posts.save(post);
        history.record(history.event(UserAction.WALL_POST_CREATED, userId).metadata("postId", post.getId().toString())
                .build());
        return new PostView(post, true);
    }

    /** @throws ApiException 404 USER_NOT_FOUND / WALL_POST_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID userId, UUID postId) {
        ProfilePermissionService.Context context = access.basic(userId);
        UserWallPost post = posts.findById(postId)
                .filter(p -> p.getUserId().equals(userId) && p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.WALL_POST_NOT_FOUND));
        ProfilePermissionService.require(mayDelete(context, post));
        post.setDeletedAt(clock.instant());
        history.record(history.event(UserAction.WALL_POST_DELETED, userId).metadata("postId", postId.toString())
                .build());
    }

    private static boolean mayDelete(ProfilePermissionService.Context context, UserWallPost post) {
        return ProfileRules.mayDeleteWallPost(context.actor().userId(), context.orga(), context.administers(),
                post.getUserId(), post.getAuthorId());
    }
}
