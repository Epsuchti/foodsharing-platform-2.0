package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.FreeSlotStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.GroceryRepository;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreGroceryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Region store list (B2): default cooperation filter, team status / search filters, subregions, my membership. */
class StoreListServiceTest {

    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID CHILD = UUID.fromString("00000000-0000-4000-8000-000000000003");

    StoreRepository stores = mock(StoreRepository.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    StorePickupInfo pickupInfo = mock(StorePickupInfo.class);
    RegionService regions = mock(RegionService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    PermissionService permissions = mock(PermissionService.class);
    StoreQueryService service;
    SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER, "a@b.c");
    List<Store> inRegion = new ArrayList<>();
    List<Store> inChild = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new StoreQueryService(stores, memberships, mock(StoreCategoryRepository.class),
                mock(StoreChainRepository.class), mock(GroceryRepository.class), mock(StoreGroceryRepository.class),
                mock(PickupWeightCategoryRepository.class), mock(StoreService.class), rights, pickupInfo, regions,
                currentActor, permissions);
        when(currentActor.requirePrincipal()).thenReturn(actor);
        when(permissions.isMemberOf(actor, REGION)).thenReturn(true);
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION, CHILD));
        when(stores.findLiveInRegions(List.of(REGION))).thenReturn(inRegion);
        when(stores.findLiveInRegions(List.of(REGION, CHILD))).thenAnswer(i -> {
            List<Store> all = new ArrayList<>(inRegion);
            all.addAll(inChild);
            return all;
        });
        Region region = new Region();
        region.setId(REGION);
        when(regions.findAll(any())).thenReturn(List.of(region));
        for (CooperationStatus status : CooperationStatus.values()) {
            inRegion.add(store(status.name(), status, TeamSearchStatus.OPEN));
        }
        inChild.add(store("Child Bäckerei", CooperationStatus.COOPERATION_ESTABLISHED, TeamSearchStatus.CLOSED));
    }

    private List<String> names(List<CooperationStatus> statuses, List<TeamSearchStatus> team, String search,
                               boolean subregions) {
        return service.regionStores(REGION, statuses, team, search, subregions, null, null).getContent().stream()
                .map(i -> i.store().getName()).toList();
    }

    @Test
    void defaultFilterIsTheFourActiveStatuses() {
        assertThat(names(null, null, null, false)).containsExactlyInAnyOrder("UNCLEAR", "NO_CONTACT",
                "IN_NEGOTIATION", "COOPERATION_ESTABLISHED");
        assertThat(names(List.of(CooperationStatus.PERMANENTLY_CLOSED), null, null, false))
                .containsExactly("PERMANENTLY_CLOSED");
    }

    @Test
    void subregionsTeamStatusAndSearch() {
        assertThat(names(null, null, null, true)).contains("Child Bäckerei").hasSize(5);
        assertThat(names(null, List.of(TeamSearchStatus.CLOSED), null, true)).containsExactly("Child Bäckerei");
        assertThat(names(null, null, "  bäck ", true)).containsExactly("Child Bäckerei");
    }

    @Test
    void outsidersOfTheRegionAreRejectedUnlessCoordinating() {
        when(permissions.isMemberOf(actor, REGION)).thenReturn(false);
        assertThat(StoreServiceTest.codeOf(() -> names(null, null, null, false))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(rights.mayCoordinateRegionStores(actor, REGION)).thenReturn(true);
        assertThat(names(null, null, null, false)).hasSize(4);
    }

    @Test
    void membershipAndSlotInfoPerStore() {
        Store established = inRegion.get(CooperationStatus.COOPERATION_ESTABLISHED.ordinal());
        StoreMembership m = new StoreMembership();
        m.setStoreId(established.getId());
        m.setUserId(actor.userId());
        m.setStatus(StoreMembershipStatus.JUMPER);
        when(memberships.findByUserId(actor.userId())).thenReturn(List.of(m));
        var slot = new StorePickupInfo.SlotInfo(null, FreeSlotStatus.RED, 1);
        when(pickupInfo.slotInfo(any())).thenReturn(Map.of(established.getId(), slot));
        var items = service.regionStores(REGION, null, null, null, false, null, null).getContent();
        var mine = items.stream().filter(i -> i.store() == established).findFirst().orElseThrow();
        assertThat(mine.membership()).isEqualTo(UserTeamStatus.WAITING_LIST);
        assertThat(mine.slots()).isEqualTo(slot);
        assertThat(items.stream().filter(i -> i.store() != established))
                .allMatch(i -> i.membership() == UserTeamStatus.NO_MEMBER && i.slots() == null);
    }

    @Test
    void myStoresKeepsApplicationsAndDropsClosedStoresWhenAsked() {
        Store applied = inRegion.get(0);
        Store member = inRegion.get(1);
        Store closed = inRegion.get(2);
        closed.setCooperationStatus(CooperationStatus.PERMANENTLY_CLOSED);
        when(memberships.findByUserId(actor.userId())).thenReturn(List.of(membership(applied,
                StoreMembershipStatus.APPLIED), membership(member, StoreMembershipStatus.MEMBER),
                membership(closed, StoreMembershipStatus.MEMBER)));
        when(stores.findLiveIn(List.of(applied.getId(), member.getId(), closed.getId())))
                .thenReturn(List.of(applied, member, closed));
        assertThat(service.myStores(true)).extracting(item -> item.store().getId())
                .containsExactly(applied.getId(), member.getId());
        assertThat(service.myStores(false)).hasSize(3);
    }

    private StoreMembership membership(Store store, StoreMembershipStatus status) {
        StoreMembership m = new StoreMembership();
        m.setStoreId(store.getId());
        m.setUserId(actor.userId());
        m.setStatus(status);
        return m;
    }

    private static Store store(String name, CooperationStatus status, TeamSearchStatus team) {
        Store store = new Store();
        store.setId(UUID.randomUUID());
        store.setName(name);
        store.setCity("Zürich");
        store.setRegionId(REGION);
        store.setCooperationStatus(status);
        store.setTeamStatus(team);
        return store;
    }
}
