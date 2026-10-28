package network.foodsharing.forum.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ForumThreadFollow;
import network.foodsharing.forum.repository.ForumPostRepository;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.forum.service.ForumPermissionService.ForumContext;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forum search (F5): case-insensitive substring (ILIKE) on thread titles, and post bodies when {@code searchBody};
 * hidden posts and inactive threads only for moderators; snippet = ±60 characters around the first body match.
 */
@Service
@Transactional(readOnly = true)
public class ForumSearchService {

    static final int SNIPPET_RADIUS = 60;

    private final ForumThreadRepository threads;
    private final ForumPostRepository posts;
    private final ForumPermissionService access;
    private final ThreadService threadService;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;

    public ForumSearchService(ForumThreadRepository threads, ForumPostRepository posts, ForumPermissionService access,
                              ThreadService threadService, CurrentActorService currentActor,
                              PermissionService permissions) {
        this.threads = threads;
        this.posts = posts;
        this.access = access;
        this.threadService = threadService;
        this.currentActor = currentActor;
        this.permissions = permissions;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE, 400 VALIDATION_FAILED (blank query) */
    public ForumViews.SearchResult searchRegion(UUID regionId, boolean ambassadorForum, String q, boolean searchBody,
                                                int page, int size) {
        ForumContext forum = access.accessibleForum(regionId, ambassadorForum);
        boolean moderator = ForumRules.mayModerate(forum.actor(), forum.forum());
        return search(q, searchBody, moderator, false, regionId, ambassadorForum, forum.actor().userId(), page, size);
    }

    /** Every forum (ORGA). @throws ApiException 403 ACCESS_DENIED, 400 VALIDATION_FAILED */
    public ForumViews.SearchResult searchAll(String q, boolean searchBody, int page, int size) {
        if (!permissions.isOrga(currentActor.requirePrincipal())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return search(q, searchBody, true, true, new UUID(0L, 0L), false, currentActor.requireUserId(), page, size);
    }

    private ForumViews.SearchResult search(String q, boolean searchBody, boolean moderator, boolean all,
                                           UUID regionId, boolean ambassadorForum, UUID userId, int page, int size) {
        if (q == null || q.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        String needle = q.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + escape(needle) + "%";
        Page<ForumThread> result = threads.search(pattern, searchBody, moderator, all, regionId, ambassadorForum,
                PageRequest.of(Math.max(0, page), ThreadService.clamp(size)));
        Map<UUID, ForumThreadFollow> follows = threadService.followsOf(userId, result.getContent());
        List<ForumViews.SearchHit> hits = new ArrayList<>();
        for (ForumThread thread : result.getContent()) {
            String snippet = null;
            if (searchBody && !thread.getTitle().toLowerCase(Locale.ROOT).contains(needle)) {
                snippet = posts.matching(thread.getId(), pattern, moderator).stream().findFirst()
                        .map(ForumPost::getBody).map(body -> snippet(body, needle)).orElse(null);
            }
            hits.add(new ForumViews.SearchHit(thread, snippet, follows.get(thread.getId())));
        }
        return new ForumViews.SearchResult(result, hits);
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** ±{@value #SNIPPET_RADIUS} characters around the first match, with ellipses where cut. */
    static String snippet(String body, String needle) {
        int at = body.toLowerCase(Locale.ROOT).indexOf(needle);
        if (at < 0) {
            return null;
        }
        int start = Math.max(0, at - SNIPPET_RADIUS);
        int end = Math.min(body.length(), at + needle.length() + SNIPPET_RADIUS);
        return (start > 0 ? "…" : "") + body.substring(start, end).replaceAll("\\s+", " ").strip()
                + (end < body.length() ? "…" : "");
    }
}
