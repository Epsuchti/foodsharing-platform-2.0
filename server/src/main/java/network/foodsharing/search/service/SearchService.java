package network.foodsharing.search.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.search.domain.SearchKind;
import network.foodsharing.search.repository.SearchIndexRepository;
import network.foodsharing.search.repository.SearchIndexRepository.PostHit;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Global search (C5, decision 147) over {@code search_index}. Every term (normalised like the index) must match; the
 * matching and ranking per database are in {@link SearchIndexRepository}. Visibility per actor
 * ({@link SearchPermissions}): ORGA sees everything; others see public rows (regions, groups, FSPs, public events),
 * stores / events / polls / threads of their regions, ambassador threads of the regions they administrate and the
 * users who are their contacts. A query with {@code @} searches users by their exact email only: ORGA all users,
 * holders of CAN_MANAGE_USERS the active members of those regions, everyone else nothing. With SEARCH_FORUM_POSTS on,
 * threads are also found through the text of their visible posts (snippet as subtitle). At most
 * {@code app.search.rate-limit-per-minute} searches per user and minute (429 TOO_MANY_REQUESTS + Retry-After).
 */
@Service
public class SearchService {

    static final int LIMIT_PER_KIND = 20;
    static final int MAX_TERMS = 5;
    static final int SNIPPET_RADIUS = 60;

    public record Index(List<SearchHitRow> regions, List<SearchHitRow> groups, List<SearchHitRow> stores, List<SearchHitRow> foodSharePoints,
                        List<SearchHitRow> conversations) {
    }

    private final SearchIndexRepository index;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final FeatureToggleService toggles;
    private final AppProperties properties;
    private final Clock clock;
    /** Null when the limit is off ({@code app.search.rate-limit-per-minute=0}). */
    private final SlidingWindowRateLimiter limiter;

    public SearchService(SearchIndexRepository index, RegionService regions, CurrentActorService currentActor,
                         PermissionService permissions, FeatureToggleService toggles, AppProperties properties,
                         Clock clock) {
        this.index = index;
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.toggles = toggles;
        this.properties = properties;
        this.clock = clock;
        int perMinute = properties.getSearch().getRateLimitPerMinute();
        this.limiter = perMinute > 0 ? new SlidingWindowRateLimiter("search", clock, perMinute, Duration.ofMinutes(1))
                : null;
    }

    /** Normalised search terms: split on whitespace, at most five, each at least min-length characters. */
    public static List<String> terms(String query, int minLength) {
        String normalized = SearchIndexer.normalize(query);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" ")).filter(t -> t.length() >= minLength).distinct().limit(MAX_TERMS)
                .toList();
    }

    /**
     * @throws ApiException 404 FEATURE_DISABLED, 429 TOO_MANY_REQUESTS (rate limit), 400 VALIDATION_FAILED (no term of
     *                      the minimum length)
     */
    @Transactional(readOnly = true)
    public Map<SearchKind, List<SearchHitRow>> search(String query, Collection<SearchKind> kinds) {
        toggles.requireEnabled(FeatureToggle.GLOBAL_SEARCH);
        SessionPrincipal principal = currentActor.requirePrincipal();
        if (limiter != null && !limiter.tryAcquire(principal.userId().toString())) {
            throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_REQUESTS, limiter.window());
        }
        List<String> terms = terms(query, Math.max(1, properties.getSearch().getMinLength()));
        if (terms.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        boolean orga = permissions.isOrga(principal);
        SearchPermissions scope = SearchPermissions.of(orga, regions.activeRegionIdsOf(principal.userId()),
                regions.adminRegionIds(principal.userId()));
        SearchIndexRepository.Scope sqlScope = new SearchIndexRepository.Scope(principal.userId(), scope.unrestricted(),
                scope.memberRegionIds(), scope.adminRegionIds());
        boolean email = query.contains("@");
        Map<SearchKind, List<SearchHitRow>> result = new EnumMap<>(SearchKind.class);
        for (SearchKind kind : SearchKind.values()) {
            boolean wanted = kinds == null || kinds.isEmpty() || kinds.contains(kind);
            List<SearchHitRow> hits;
            if (!wanted) {
                hits = List.of();
            } else if (kind == SearchKind.USER && email) {
                Set<UUID> emailScope = orga ? null : permissions.regionIdsWith(principal, Permission.CAN_MANAGE_USERS);
                hits = index.usersByEmail(query, emailScope, LIMIT_PER_KIND);
            } else {
                hits = index.search(kind, terms, sqlScope, clock.instant(), LIMIT_PER_KIND);
                if (kind == SearchKind.THREAD && toggles.isEnabled(FeatureToggle.SEARCH_FORUM_POSTS)) {
                    hits = withPostHits(hits, index.threadsByPostContent(terms, sqlScope, LIMIT_PER_KIND * 3), terms);
                }
            }
            result.put(kind, hits);
        }
        return result;
    }

    /** Title hits first, then threads found through a post (first post per thread, snippet subtitle); max 20. */
    static List<SearchHitRow> withPostHits(List<SearchHitRow> titleHits, List<PostHit> postHits, List<String> terms) {
        List<SearchHitRow> merged = new ArrayList<>(titleHits);
        Set<UUID> seen = new LinkedHashSet<>();
        titleHits.forEach(hit -> seen.add(hit.entityId()));
        for (PostHit post : postHits) {
            if (merged.size() >= LIMIT_PER_KIND) {
                break;
            }
            SearchHitRow thread = post.thread();
            if (seen.add(thread.entityId())) {
                String snippet = snippet(post.body(), terms);
                merged.add(new SearchHitRow(thread.kind(), thread.entityId(), thread.title(),
                        snippet == null ? thread.subtitle() : snippet, thread.link(), thread.imageUrl()));
            }
        }
        return merged;
    }

    /**
     * ±{@value #SNIPPET_RADIUS} characters around the first term found in the body (case- and accent-insensitive),
     * with ellipses where cut; the start of the body when no term occurs literally (stemmed match).
     */
    static String snippet(String body, List<String> terms) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String folded = foldPreservingLength(body);
        int at = -1;
        int length = 0;
        for (String term : terms) {
            int found = folded.indexOf(term);
            if (found >= 0 && (at < 0 || found < at)) {
                at = found;
                length = term.length();
            }
        }
        int start = Math.max(0, at < 0 ? 0 : at - SNIPPET_RADIUS);
        int end = Math.min(body.length(), (at < 0 ? 0 : at + length) + SNIPPET_RADIUS);
        return (start > 0 ? "…" : "") + body.substring(start, end).replaceAll("\\s+", " ").strip()
                + (end < body.length() ? "…" : "");
    }

    /** Lower case without diacritics, one character per character (indexes stay valid for the original text). */
    private static String foldPreservingLength(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            String decomposed = Normalizer.normalize(String.valueOf(value.charAt(i)), Normalizer.Form.NFD);
            out.append(decomposed.isEmpty() ? value.charAt(i)
                    : decomposed.substring(0, 1).toLowerCase(Locale.ROOT).charAt(0));
        }
        return out.toString();
    }

    /** Quick-access index of the header search: own regions, groups, stores, FSPs and conversations. */
    @Transactional(readOnly = true)
    public Index index() {
        toggles.requireEnabled(FeatureToggle.GLOBAL_SEARCH);
        UUID userId = currentActor.requirePrincipal().userId();
        List<Region> units = regions.findAll(regions.activeRegionIdsOf(userId));
        List<SearchHitRow> regionHits = units.stream().filter(r -> !r.isGroup())
                .map(r -> new SearchHitRow(SearchKind.REGION, r.getId(), r.getName(), null, "/regions/" + r.getId(), null))
                .sorted((a, b) -> a.title().compareToIgnoreCase(b.title())).toList();
        List<SearchHitRow> groupHits = units.stream().filter(Region::isGroup)
                .map(r -> new SearchHitRow(SearchKind.GROUP, r.getId(), r.getName(), null, "/groups/" + r.getId(), null))
                .sorted((a, b) -> a.title().compareToIgnoreCase(b.title())).toList();
        return new Index(regionHits, groupHits, index.ownStores(userId), index.ownFoodSharePoints(userId),
                index.ownConversations(userId, 50));
    }

    /** The per-actor visibility scope of a search (pure, unit-tested). */
    public record SearchPermissions(boolean unrestricted, Set<UUID> memberRegionIds, Set<UUID> adminRegionIds) {
        public static SearchPermissions of(boolean orga, Set<UUID> memberRegionIds, Set<UUID> adminRegionIds) {
            return new SearchPermissions(orga, Set.copyOf(memberRegionIds), Set.copyOf(adminRegionIds));
        }

        /** May an actor with this scope see an index row of the given visibility and region? */
        public boolean maySee(String visibility, UUID regionId) {
            if (unrestricted || "PUBLIC".equals(visibility)) {
                return true;
            }
            if ("ADMINS".equals(visibility)) {
                return regionId != null && adminRegionIds.contains(regionId);
            }
            return regionId != null && memberRegionIds.contains(regionId);
        }
    }
}
