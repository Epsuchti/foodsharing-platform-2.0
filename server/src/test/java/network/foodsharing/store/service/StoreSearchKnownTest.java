package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.repository.GroceryRepository;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreGroceryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Decision 167 S4 (Q3, the link picker, was LinkCandidateFlowIT): {@code searchKnown} finds live stores across regions
 * by name, city or postcode, without the store itself and without permanently closed stores; LIKE wildcards in the
 * query are literal; at most 50 per page; nothing for people who may not know stores or for fewer than two
 * characters; each hit carries its region path, root first.
 */
class StoreSearchKnownTest {

    static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Role.STORE_MANAGER, "manager@test.local");
    static final UUID SELF = UUID.randomUUID();

    StoreRepository stores = mock(StoreRepository.class);
    RegionService regions = mock(RegionService.class);
    StoreVisibility visibility = mock(StoreVisibility.class);
    StoreQueryService service;

    Region schweiz = region("Schweiz", null);
    Region kanton = region("Kanton Zürich", schweiz);
    Region winterthur = region("Winterthur", kanton);

    static Region region(String name, Region parent) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setName(name);
        region.setParentId(parent == null ? null : parent.getId());
        return region;
    }

    @BeforeEach
    void setUp() {
        CurrentActorService actor = mock(CurrentActorService.class);
        when(actor.requirePrincipal()).thenReturn(ACTOR);
        when(visibility.mayKnowStores(ACTOR)).thenReturn(true);
        List<Region> all = List.of(schweiz, kanton, winterthur);
        when(regions.findAll(anyCollection())).thenAnswer(inv -> all.stream()
                .filter(r -> inv.<Collection<UUID>>getArgument(0).contains(r.getId())).toList());
        service = new StoreQueryService(stores, mock(StoreMembershipRepository.class),
                mock(StoreCategoryRepository.class), mock(StoreChainRepository.class), mock(GroceryRepository.class),
                mock(StoreGroceryRepository.class), mock(PickupWeightCategoryRepository.class),
                mock(StoreService.class), mock(StorePermissionService.class), mock(StorePickupInfo.class), regions,
                actor, mock(PermissionService.class), visibility);
    }

    Store store(String name, Region region) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setName(name);
        store.setRegionId(region.getId());
        return store;
    }

    @Test
    void searchesLiveStoresWithoutItselfAndWithoutPermanentlyClosedOnesAndGivesTheRegionPath() {
        Store coop = store("Coop Winterthur Bahnhof", winterthur);
        when(stores.searchLive(anyString(), any(), any(), any())).thenAnswer(inv ->
                new PageImpl<>(List.of(coop), inv.<Pageable>getArgument(3), 1));
        var page = service.searchKnown("  Winterthur ", SELF, 0, 20);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(stores).searchLive(pattern.capture(), eq(SELF), eq(CooperationStatus.PERMANENTLY_CLOSED), any());
        assertThat(pattern.getValue()).isEqualTo("%winterthur%");
        assertThat(page.getContent()).singleElement().satisfies(hit -> {
            assertThat(hit.store()).isSameAs(coop);
            assertThat(hit.regionPath()).containsExactly("Schweiz", "Kanton Zürich", "Winterthur");
        });
    }

    @Test
    void likeWildcardsAreLiteral() {
        when(stores.searchLive(anyString(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.searchKnown("%_\\", SELF, 0, 20);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(stores).searchLive(pattern.capture(), any(), any(), any());
        assertThat(pattern.getValue()).isEqualTo("%\\%\\_\\\\%");
    }

    @Test
    void pagesHoldAtMostFiftyAndNegativePagesStartAtZero() {
        when(stores.searchLive(anyString(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        service.searchKnown("coop", SELF, -3, 500);
        ArgumentCaptor<Pageable> request = ArgumentCaptor.forClass(Pageable.class);
        verify(stores).searchLive(anyString(), any(), any(), request.capture());
        assertThat(request.getValue().getPageNumber()).isZero();
        assertThat(request.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void nothingForOneCharacterOrForPeopleWhoMayNotKnowStores() {
        assertThat(service.searchKnown(" c ", SELF, 0, 20).getContent()).isEmpty();
        when(visibility.mayKnowStores(ACTOR)).thenReturn(false);
        assertThat(service.searchKnown("coop", SELF, 0, 20).getContent()).isEmpty();
        verify(stores, never()).searchLive(anyString(), any(), any(), any());
    }
}
