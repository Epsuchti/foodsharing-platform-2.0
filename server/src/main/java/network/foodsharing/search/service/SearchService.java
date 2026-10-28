package network.foodsharing.search.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.search.domain.SearchKind;
import network.foodsharing.search.repository.SearchIndexRepository;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Global search (C5) over {@code search_index}. Every term (normalised like the index) must occur. Visibility per actor
 * ({@link SearchPermissions}): ORGA sees everything; others see public rows (regions, groups, FSPs, public events),
 * users / stores / events / polls / threads of their regions and ambassador threads of the regions they administrate.
 */
@Service
public class SearchService {

    static final int LIMIT_PER_KIND = 20;
    static final int MAX_TERMS = 5;

    public record Index(List<SearchHitRow> regions, List<SearchHitRow> groups, List<SearchHitRow> stores, List<SearchHitRow> foodSharePoints,
                        List<SearchHitRow> conversations) {
    }

    private final SearchIndexRepository index;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final FeatureToggleService toggles;
    private final AppProperties properties;

    public SearchService(SearchIndexRepository index, RegionService regions, CurrentActorService currentActor,
                         PermissionService permissions, FeatureToggleService toggles, AppProperties properties) {
        this.index = index;
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.toggles = toggles;
        this.properties = properties;
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

    /** @throws ApiException 404 FEATURE_DISABLED, 400 VALIDATION_FAILED (no term of the minimum length) */
    @Transactional(readOnly = true)
    public Map<SearchKind, List<SearchHitRow>> search(String query, Collection<SearchKind> kinds) {
        toggles.requireEnabled(FeatureToggle.GLOBAL_SEARCH);
        SessionPrincipal principal = currentActor.requirePrincipal();
        List<String> terms = terms(query, Math.max(1, properties.getSearch().getMinLength()));
        if (terms.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        SearchPermissions scope = SearchPermissions.of(permissions.isOrga(principal),
                regions.activeRegionIdsOf(principal.userId()), regions.adminRegionIds(principal.userId()));
        Map<SearchKind, List<SearchHitRow>> result = new EnumMap<>(SearchKind.class);
        for (SearchKind kind : SearchKind.values()) {
            boolean wanted = kinds == null || kinds.isEmpty() || kinds.contains(kind);
            result.put(kind, wanted ? index.search(kind, terms, scope.unrestricted(), scope.memberRegionIds(),
                    scope.adminRegionIds(), LIMIT_PER_KIND) : List.of());
        }
        return result;
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
