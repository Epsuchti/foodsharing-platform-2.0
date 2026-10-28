package network.foodsharing.fsp.service;

import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.domain.FspWallPost;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.fsp.repository.FspWallPostRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading food share points (FS1): details, region lists, followed FSPs, the wall, map markers. */
@Service
@Transactional(readOnly = true)
public class FspQueryService {

    /** One FSP as one viewer sees it. */
    public record FspView(FoodSharePoint fsp, FspRules.Facts facts, int followerCount, FspFollower myFollow,
                          List<UUID> managerIds) {
    }

    /** A list entry. */
    public record FspSummaryView(FoodSharePoint fsp, int followerCount, boolean following) {
    }

    /** A wall post with the viewer's delete right. */
    public record PostView(FspWallPost post, boolean mayDelete) {
    }

    private final FoodSharePointRepository fsps;
    private final FspFollowerRepository followers;
    private final FspWallPostRepository posts;
    private final FspPermissionService access;
    private final CurrentActorService actors;
    private final RegionService regions;

    private final FeatureToggleService toggles;

    public FspQueryService(FoodSharePointRepository fsps, FspFollowerRepository followers,
                           FspWallPostRepository posts, FspPermissionService access, CurrentActorService actors,
                           RegionService regions,
            FeatureToggleService toggles) {
        this.toggles = toggles;
        this.fsps = fsps;
        this.followers = followers;
        this.posts = posts;
        this.access = access;
        this.actors = actors;
        this.regions = regions;
    }

    /** @throws ApiException 404 FSP_NOT_FOUND (also deleted or not visible) */
    public FspView get(UUID fspId) {
        toggles.requireEnabled(FeatureToggle.FOOD_SHARE_POINTS);
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspRules.Facts facts = access.requireVisible(actor, fsp);
        List<FspFollower> rows = followers.findByFspId(fspId);
        FspFollower mine = rows.stream().filter(r -> r.getUserId().equals(actor.userId())).findFirst().orElse(null);
        List<UUID> managers = rows.stream().filter(r -> r.getRole() == FspFollowerRole.MANAGER)
                .map(FspFollower::getUserId).toList();
        return new FspView(fsp, facts, rows.size(), mine, managers);
    }

    /**
     * FSPs of exactly this region (upstream lists the region only), by name. Inactive ones only for viewers who may
     * activate them in the region, and for their creator and managers.
     *
     * @throws ApiException 404 REGION_NOT_FOUND
     */
    public List<FspSummaryView> ofRegion(UUID regionId, boolean includeInactive) {
        toggles.requireEnabled(FeatureToggle.FOOD_SHARE_POINTS);
        SessionPrincipal actor = actors.requirePrincipal();
        regions.require(regionId);
        boolean mayAdd = includeInactive && access.mayAdd(actor, regionId);
        List<FoodSharePoint> list = fsps.findByRegionIdAndDeletedAtIsNullOrderByNameAsc(regionId);
        Set<UUID> managed = includeInactive ? followers.findByUserId(actor.userId()).stream()
                .filter(r -> r.getRole() == FspFollowerRole.MANAGER).map(FspFollower::getFspId)
                .collect(Collectors.toSet()) : Set.of();
        return summaries(list.stream().filter(f -> f.getStatus() == FspStatus.ACTIVE || mayAdd
                || (includeInactive && (managed.contains(f.getId()) || actor.userId().equals(f.getCreatedByUserId()))))
                .toList(), actor.userId());
    }

    /** FSPs the current user follows or manages. */
    public List<FspSummaryView> mine() {
        toggles.requireEnabled(FeatureToggle.FOOD_SHARE_POINTS);
        UUID userId = actors.requireUserId();
        Set<UUID> ids = followedIds(userId);
        return ids.isEmpty() ? List.of() : summaries(fsps.findByIdInAndDeletedAtIsNullOrderByNameAsc(ids), userId);
    }

    /** Ids of the FSPs the user follows or manages (activity feed). */
    public Set<UUID> followedIds(UUID userId) {
        return followers.findByUserId(userId).stream().map(FspFollower::getFspId).collect(Collectors.toSet());
    }

    /** @throws ApiException 404 FSP_NOT_FOUND */
    public Page<PostView> wall(UUID fspId, int page, int size) {
        toggles.requireEnabled(FeatureToggle.FOOD_SHARE_POINTS);
        SessionPrincipal actor = actors.requirePrincipal();
        FoodSharePoint fsp = require(fspId);
        FspRules.Facts facts = access.requireVisible(actor, fsp);
        return posts.findByFspIdAndDeletedAtIsNullOrderByCreatedAtDesc(fspId, PageRequest.of(page, size))
                .map(post -> new PostView(post, FspRules.mayDeletePost(facts,
                        post.getAuthorId().equals(actor.userId()))));
    }

    /** Active, not deleted FSPs with coordinates (map layer). */
    public List<FoodSharePoint> activeWithCoordinates() {
        return fsps.findByStatusAndDeletedAtIsNull(FspStatus.ACTIVE).stream()
                .filter(f -> f.getLat() != null && f.getLon() != null).toList();
    }

    /** An active FSP (map bubble). */
    public Optional<FoodSharePoint> findActive(UUID fspId) {
        return fsps.findById(fspId).filter(f -> f.getDeletedAt() == null && f.getStatus() == FspStatus.ACTIVE);
    }

    public int followerCount(UUID fspId) {
        return (int) followers.countByFspId(fspId);
    }

    /** Active FSPs in the given regions (region statistics). */
    public long countActive(Collection<UUID> regionIds) {
        return regionIds.isEmpty() ? 0 : fsps.countInRegions(regionIds, FspStatus.ACTIVE);
    }

    private List<FspSummaryView> summaries(List<FoodSharePoint> list, UUID userId) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : followers.countByFspIds(list.stream().map(FoodSharePoint::getId).toList())) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        Set<UUID> mine = followedIds(userId);
        return list.stream().map(f -> new FspSummaryView(f, counts.getOrDefault(f.getId(), 0),
                mine.contains(f.getId()))).toList();
    }

    private FoodSharePoint require(UUID fspId) {
        return fsps.findById(fspId).filter(f -> f.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND));
    }

    /** A wall post for the activity feed (A19). */
    public record FeedPost(UUID id, UUID fspId, String fspName, UUID authorId, String body,
                           java.time.Instant createdAt) {
    }

    /** Activity feed (A19): newest wall posts of the FSPs the user follows or manages. */
    public List<FeedPost> latestOfFollowed(UUID userId, java.time.Instant before, int limit) {
        Set<UUID> ids = followedIds(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = new HashMap<>();
        fsps.findByIdInAndDeletedAtIsNullOrderByNameAsc(ids).forEach(f -> names.put(f.getId(), f.getName()));
        if (names.isEmpty()) {
            return List.of();
        }
        return posts.findByFspIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDesc(names.keySet(), before,
                        PageRequest.of(0, limit)).stream()
                .map(p -> new FeedPost(p.getId(), p.getFspId(), names.get(p.getFspId()), p.getAuthorId(),
                        p.getBody(), p.getCreatedAt())).toList();
    }
}
