package network.foodsharing.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.search.domain.SearchHitRow;
import network.foodsharing.search.domain.SearchKind;
import network.foodsharing.search.repository.SearchIndexRepository;
import network.foodsharing.search.repository.SearchIndexRepository.PostHit;
import network.foodsharing.search.repository.SearchIndexRepository.Scope;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Decision 147: search scope, email permission, forum post merge and snippet, rate limit. */
class SearchServiceTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID REGION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final SearchIndexRepository index = mock(SearchIndexRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final FeatureToggleService toggles = mock(FeatureToggleService.class);
    private final AppProperties properties = new AppProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private SessionPrincipal principal;

    @BeforeEach
    void setUp() {
        actAs(Role.FOODSAVER);
        when(regions.activeRegionIdsOf(ME)).thenReturn(Set.of(REGION));
        when(regions.adminRegionIds(ME)).thenReturn(Set.of());
        when(index.search(any(), anyList(), any(), any(), anyInt())).thenReturn(List.of());
        when(index.usersByEmail(any(), any(), anyInt())).thenReturn(List.of());
    }

    private void actAs(Role role) {
        principal = new SessionPrincipal(ME, UUID.randomUUID(), role, "me@local.test");
        when(actors.requirePrincipal()).thenReturn(principal);
        when(permissions.isOrga(principal)).thenReturn(role == Role.ORGA);
    }

    private boolean mayKnowStores = true;

    private SearchService service(int perMinute) {
        properties.getSearch().setRateLimitPerMinute(perMinute);
        network.foodsharing.store.service.StoreVisibility visibility =
                org.mockito.Mockito.mock(network.foodsharing.store.service.StoreVisibility.class);
        when(visibility.mayKnowStores(org.mockito.ArgumentMatchers.any())).thenReturn(mayKnowStores);
        return new SearchService(index, regions, actors, permissions, toggles, visibility, properties, clock);
    }

    @Test
    void searchesEveryKindWithTheActorsScopeAndTheClock() {
        service(0).search("Bäckerei Zürich", List.of());

        Scope scope = new Scope(ME, false, Set.of(REGION), Set.of());
        for (SearchKind kind : SearchKind.values()) {
            verify(index).search(eq(kind), eq(List.of("backerei", "zurich")), eq(scope), eq(NOW), eq(20));
        }
        verify(index, never()).usersByEmail(any(), any(), anyInt());
    }

    @Test
    void emailQueriesAreExactAndScopedByPermission() {
        SearchService service = service(0);

        // without CAN_MANAGE_USERS: an empty scope, so nothing is found by email
        when(permissions.regionIdsWith(principal, Permission.CAN_MANAGE_USERS)).thenReturn(Set.of());
        service.search("nina@local.test", List.of(SearchKind.USER));
        verify(index).usersByEmail("nina@local.test", Set.of(), 20);
        verify(index, never()).search(eq(SearchKind.USER), anyList(), any(), any(), anyInt());

        // CAN_MANAGE_USERS: only members of those regions
        when(permissions.regionIdsWith(principal, Permission.CAN_MANAGE_USERS)).thenReturn(Set.of(REGION));
        service.search("Nina@Local.test", List.of(SearchKind.USER));
        verify(index).usersByEmail("Nina@Local.test", Set.of(REGION), 20);

        // ORGA: everyone
        actAs(Role.ORGA);
        service.search("nina@local.test", List.of(SearchKind.USER));
        verify(index).usersByEmail(eq("nina@local.test"), isNull(), eq(20));
    }

    @Test
    void forumPostsOnlyWithTheToggle() {
        SearchService service = service(0);
        UUID thread = UUID.randomUUID();
        when(index.threadsByPostContent(anyList(), any(), anyInt())).thenReturn(List.of(new PostHit(
                new SearchHitRow(SearchKind.THREAD, thread, "Kühlschrank", "Zürich", "/forum/threads/" + thread, null),
                "Der Kühlschrank ist repariert.")));

        Map<SearchKind, List<SearchHitRow>> off = service.search("kuhlschrank", List.of(SearchKind.THREAD));
        assertThat(off.get(SearchKind.THREAD)).isEmpty();
        verify(index, never()).threadsByPostContent(anyList(), any(), anyInt());

        when(toggles.isEnabled(FeatureToggle.SEARCH_FORUM_POSTS)).thenReturn(true);
        Map<SearchKind, List<SearchHitRow>> on = service.search("kuhlschrank", List.of(SearchKind.THREAD));
        assertThat(on.get(SearchKind.THREAD)).singleElement()
                .satisfies(hit -> assertThat(hit.subtitle()).isEqualTo("Der Kühlschrank ist repariert."));
    }

    @Test
    void postHitsFollowTitleHitsOncePerThread() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        SearchHitRow titleHit = new SearchHitRow(SearchKind.THREAD, a, "A", "Zürich", "/a", null);
        SearchHitRow threadB = new SearchHitRow(SearchKind.THREAD, b, "B", "Zürich", "/b", null);
        List<SearchHitRow> merged = SearchService.withPostHits(List.of(titleHit), List.of(
                new PostHit(new SearchHitRow(SearchKind.THREAD, a, "A", "Zürich", "/a", null), "Brot im A"),
                new PostHit(threadB, "Neuestes Brot im B"), new PostHit(threadB, "Älteres Brot im B")), List.of("brot"));

        assertThat(merged).extracting(SearchHitRow::entityId).containsExactly(a, b);
        assertThat(merged.get(0).subtitle()).isEqualTo("Zürich");
        assertThat(merged.get(1).subtitle()).isEqualTo("Neuestes Brot im B");
    }

    @Test
    void snippetAroundTheFirstTermIgnoringCaseAndAccents() {
        String body = "x".repeat(100) + " Der Kühlschrank   ist leer " + "y".repeat(100);
        String snippet = SearchService.snippet(body, List.of("kuhlschrank"));
        assertThat(snippet).startsWith("…").endsWith("…").contains("Der Kühlschrank ist leer");
        assertThat(SearchService.snippet("Kurz und gut", List.of("fehlt"))).isEqualTo("Kurz und gut");
        assertThat(SearchService.snippet(" ", List.of("x"))).isNull();
    }

    @Test
    void transliteratedUmlautsAreIndexedTwice() {
        assertThat(SearchIndexer.withFoldedWords("baeckereien zuerich nord")).isEqualTo(
                "baeckereien zuerich nord backereien zurich");
        assertThat(SearchIndexer.withFoldedWords("zurich")).isEqualTo("zurich");
    }

    @Test
    void storesIndexTheirAddressSeparately() {
        SearchIndexRepository.Source store = new SearchIndexRepository.Source(SearchKind.STORE, UUID.randomUUID(),
                "Coop Wiedikon", "Zürich", null, "Birmensdorferstrasse 1 8003 Zürich", REGION, "REGION_MEMBERS",
                "/stores/x", null, true);
        assertThat(SearchIndexer.textOf(store)).isEqualTo("coop wiedikon");
        SearchIndexRepository.Source fsp = new SearchIndexRepository.Source(SearchKind.FSP, UUID.randomUUID(),
                "Fairteiler", "Zürich", "Gasse 1", null, REGION, "PUBLIC", "/fsp/x", null, true);
        assertThat(SearchIndexer.textOf(fsp)).isEqualTo("fairteiler zurich gasse 1");
    }

    @Test
    void rateLimitPerUserWith429AndRetryAfter() {
        SearchService service = service(2);
        service.search("zurich", List.of());
        service.search("zurich", List.of());

        assertThatThrownBy(() -> service.search("zurich", List.of())).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getCode()).isEqualTo(ApiErrorCode.TOO_MANY_REQUESTS);
            assertThat(e.getRetryAfter()).isEqualTo(java.time.Duration.ofMinutes(1));
        });
        // 0 = off
        SearchService unlimited = service(0);
        for (int i = 0; i < 5; i++) {
            unlimited.search("zurich", List.of());
        }
    }
}
