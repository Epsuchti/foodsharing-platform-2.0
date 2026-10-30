package network.foodsharing.store.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreWallPost;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.domain.WallMilestone;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.store.repository.StoreWallPostRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Store wall (plan §13.3 S11, A19 wall rules): list, post, delete. Delete: ORGA everything; the author always; posts
 * older than {@code app.store.wall-delete-after-days}: store managers (COORDINATOR) and whoever coordinates the
 * store's region (ambassadors / store coordination admins). Store milestones ({@link StoreService.StoreChanged}) are
 * written as wall posts after the store change has committed.
 */
@Service
public class StoreWallService {

    public record PostView(StoreWallPost post, User author, boolean mayDelete) {
    }

    private final StoreWallPostRepository posts;
    private final StoreRepository stores;
    private final StoreService storeService;
    private final StorePermissionService rights;
    private final PermissionService permissions;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public StoreWallService(StoreWallPostRepository posts, StoreRepository stores, StoreService storeService,
                            StorePermissionService rights, PermissionService permissions, UserService users,
                            CurrentActorService currentActor, HistoryService history, AppProperties properties,
                            Clock clock) {
        this.posts = posts;
        this.stores = stores;
        this.storeService = storeService;
        this.rights = rights;
        this.permissions = permissions;
        this.users = users;
        this.currentActor = currentActor;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** @throws ApiException 404 STORE_NOT_FOUND / STORE_DELETED, 403 unless mayReadWall */
    @Transactional(readOnly = true)
    public Page<PostView> list(UUID storeId, Integer page, Integer size) {
        Store store = storeService.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.Rights r = rights.rights(actor, store);
        StorePermissionService.require(r.mayReadWall());
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.max(1, Math.min(size, 100));
        Page<StoreWallPost> result = posts.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(storeId,
                PageRequest.of(p, s));
        Map<UUID, User> authors = users.findAll(result.getContent().stream().map(StoreWallPost::getAuthorId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        boolean orga = permissions.isOrga(actor);
        return result.map(post -> new PostView(post, post.getAuthorId() == null ? null : authors.get(post.getAuthorId()),
                mayDelete(actor.userId(), orga, r, post)));
    }

    /** @throws ApiException 404, 403 unless mayPostWall, 400 VALIDATION_FAILED for blank / too long text */
    @Transactional
    public PostView post(UUID storeId, String text) {
        Store store = storeService.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.Rights r = rights.rights(actor, store);
        StorePermissionService.require(r.mayPostWall());
        if (text == null || text.isBlank() || text.strip().length() > 4000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        StoreWallPost post = save(storeId, actor.userId(), text.strip(), WallMilestone.NONE);
        history.record(history.event(StoreLogAction.WALL_POST_ADDED, storeId).region(store.getRegionId())
                .metadata("postId", post.getId().toString()).build());
        return new PostView(post, users.findAll(List.of(actor.userId())).stream().findFirst().orElse(null), true);
    }

    /** @throws ApiException 404 STORE_NOT_FOUND / STORE_DELETED / WALL_POST_NOT_FOUND, 403 per A19 */
    @Transactional
    public void delete(UUID storeId, UUID postId) {
        Store store = storeService.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.Rights r = rights.rights(actor, store);
        StorePermissionService.require(r.mayReadWall());
        StoreWallPost post = posts.findByIdAndStoreIdAndDeletedAtIsNull(postId, storeId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.WALL_POST_NOT_FOUND));
        StorePermissionService.require(mayDelete(actor.userId(), permissions.isOrga(actor), r, post));
        post.setDeletedAt(clock.instant());
        post.setDeletedBy(actor.userId());
        history.record(history.event(StoreLogAction.DELETED_FROM_WALL, storeId).region(store.getRegionId())
                .secondarySubject(post.getAuthorId()).metadata("postId", post.getId().toString())
                .metadata("text", post.getText()).build());
    }

    /** A19 wall delete rule. */
    boolean mayDelete(UUID actorId, boolean orga, StorePermissionService.Rights r, StoreWallPost post) {
        if (orga) {
            return true;
        }
        if (!r.mayReadWall()) {
            return false;
        }
        if (actorId.equals(post.getAuthorId())) {
            return true;
        }
        Instant created = post.getCreatedAt() == null ? clock.instant() : post.getCreatedAt();
        boolean old = created.isBefore(clock.instant().minus(Duration.ofDays(properties.getStore()
                .getWallDeleteAfterDays())));
        return old && (r.teamStatus() == UserTeamStatus.COORDINATOR || r.coordinate());
    }

    /** Soft-deletes every live post of a deleted store (no own log row; DELETE_STORE covers it). */
    @Transactional(propagation = Propagation.MANDATORY)
    @network.foodsharing.history.NoHistory(reason = "part of StoreService.delete, which records DELETE_STORE")
    public int deleteAllOf(UUID storeId, UUID actorId) {
        List<StoreWallPost> live = posts.findByStoreIdAndDeletedAtIsNull(storeId);
        for (StoreWallPost post : live) {
            post.setDeletedAt(clock.instant());
            post.setDeletedBy(actorId);
        }
        return live.size();
    }

    /** Milestones and the first post of a new store, written after the store change committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @network.foodsharing.history.NoHistory(reason = "system wall note of a store change that recorded its own history")
    public void onStoreChanged(StoreService.StoreChanged event) {
        stores.findLive(event.storeId()).ifPresent(store -> save(event.storeId(), event.authorId(),
                event.text() == null || event.text().isBlank() ? store.getName() : event.text(), event.milestone()));
    }

    private StoreWallPost save(UUID storeId, UUID authorId, String text, WallMilestone milestone) {
        StoreWallPost post = new StoreWallPost();
        post.setStoreId(storeId);
        post.setAuthorId(authorId);
        post.setText(text);
        post.setMilestone(milestone);
        return posts.save(post);
    }

    /** A wall post for the activity feed (A19). */
    public record FeedPost(UUID id, UUID storeId, String storeName, UUID authorId, String text,
                           java.time.Instant createdAt) {
    }

    /** Activity feed (A19): newest wall posts of the stores whose team the user is in (member or jumper). */
    @Transactional(readOnly = true)
    public List<FeedPost> latestOfMyStores(UUID userId, java.time.Instant before, int limit) {
        List<UUID> ids = storeService.teamStoreIds(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        stores.findLiveIn(ids).forEach(st -> names.put(st.getId(), st.getName()));
        return posts.findByStoreIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(names.keySet(), before,
                        org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(p -> new FeedPost(p.getId(), p.getStoreId(), names.get(p.getStoreId()), p.getAuthorId(),
                        p.getText(), p.getCreatedAt())).toList();
    }
}
