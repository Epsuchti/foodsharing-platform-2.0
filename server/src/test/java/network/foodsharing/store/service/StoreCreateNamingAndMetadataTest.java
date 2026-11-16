package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.PublicityStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreGrocery;
import network.foodsharing.store.domain.StoreLogAction;
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
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Store rules the deleted StoreApiIT, StoreChainApiIT and StorePublicNamingIT asserted through the API (decision
 * 180): creating a store (right, creator becomes the manager, default prefetch, STORE_CREATED), the public naming
 * switch inside an update (only ORGA or the store's manager, history on every change, cleared for any editor when the
 * publicity leaves ALLOWED), grocery lists (absent keeps, empty clears) and the store metadata (foodsavers only, chains
 * only for people who may know stores).
 */
class StoreCreateNamingAndMetadataTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.fromString("00000020-0001-4000-8000-000000000001");
    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-000000000002");

    StoreRepository stores = mock(StoreRepository.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    StoreGroceryRepository storeGroceries = mock(StoreGroceryRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    RegionService regions = mock(RegionService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    List<Object> published = new ArrayList<>();
    SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.STORE_MANAGER, "a@b.c");
    StoreService service;
    Store store;

    @BeforeEach
    void setUp() {
        service = new StoreService(stores, memberships, mock(StoreCategoryRepository.class),
                mock(StoreChainRepository.class), mock(GroceryRepository.class), storeGroceries,
                mock(PickupWeightCategoryRepository.class), rights, regions, mock(RegionPermissionResolver.class),
                mock(UserService.class), currentActor, history, published::add, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), StoreServiceTest.provider(mock(StorePickupInfo.class)),
                StoreServiceTest.provider(mock(StoreWallService.class)));
        when(currentActor.requirePrincipal()).thenReturn(actor);
        Region region = new Region();
        region.setId(REGION);
        when(regions.require(REGION)).thenReturn(region);
        store = new Store();
        store.setId(STORE);
        store.setName("Coop");
        store.setRegionId(REGION);
        store.setTeamStatus(TeamSearchStatus.OPEN);
        store.setCooperationStatus(CooperationStatus.COOPERATION_ESTABLISHED);
        store.setPublicity(PublicityStatus.ALLOWED);
        store.setPrefetchDays(14);
        when(stores.findById(STORE)).thenReturn(Optional.of(store));
    }

    static ApiErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e.getCode();
        }
        return null;
    }

    static StoreService.StoreInput input(String name, CooperationStatus status, PublicityStatus publicity,
                                         List<UUID> groceryIds, Boolean publiclyNamed) {
        return new StoreService.StoreInput(name, null, null, null, null, null, null, null, null, groceryIds, status,
                null, null, null, null, null, null, publicity, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, publiclyNamed);
    }

    /** Edit rights of a team editor; {@code orga} = mayDeleteAnyWallPost, {@code manager} = isManager. */
    static StorePermissionService.Rights editor(boolean orga, boolean manager) {
        return new StorePermissionService.Rights(true, true, true, orga, true, true, true, true, true, true, true,
                true, true, orga, false, true, true, false, false, manager, true,
                manager ? UserTeamStatus.COORDINATOR : UserTeamStatus.NO_MEMBER);
    }

    @Test
    void creatingAStoreNeedsTheCreateRight() {
        when(rights.mayCreateStore(actor, REGION)).thenReturn(false);
        assertThat(codeOf(() -> service.create(REGION, input("X", CooperationStatus.UNCLEAR, null, null, null))))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verify(stores, never()).save(any());
        verify(memberships, never()).save(any());
    }

    @Test
    void theCreatorManagesTheNewStoreWithTheDefaultPrefetchAndItIsLogged() {
        when(rights.mayCreateStore(actor, REGION)).thenReturn(true);
        assertThat(codeOf(() -> service.create(REGION, input(" ", CooperationStatus.UNCLEAR, null, null, null))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        Store created = service.create(REGION, input("Neuer Laden", CooperationStatus.IN_NEGOTIATION, null, null,
                null));

        assertThat(created.getPrefetchDays()).isEqualTo(14);
        assertThat(created.getRegionId()).isEqualTo(REGION);
        ArgumentCaptor<StoreMembership> creator = ArgumentCaptor.forClass(StoreMembership.class);
        verify(memberships).save(creator.capture());
        assertThat(creator.getValue().getUserId()).isEqualTo(actor.userId());
        assertThat(creator.getValue().getStatus()).isEqualTo(StoreMembershipStatus.MEMBER);
        assertThat(creator.getValue().isManager()).isTrue();
        verify(history).event(StoreLogAction.STORE_CREATED, created.getId());
    }

    @Test
    void aRegionCoordinatorWhoDoesNotManageTheStoreMayNotSwitchThePublicName() {
        when(rights.rights(any(), any())).thenReturn(editor(false, false));
        assertThat(codeOf(() -> service.update(STORE, input(null, null, null, null, true))))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(store.isPubliclyNamed()).isFalse();
    }

    @Test
    void theManagerOrOrgaSwitchesItWithAHistoryEntryEachTime() {
        when(rights.rights(any(), any())).thenReturn(editor(false, true));
        service.update(STORE, input(null, null, null, null, true));
        assertThat(store.isPubliclyNamed()).isTrue();
        when(rights.rights(any(), any())).thenReturn(editor(true, false));
        service.update(STORE, input(null, null, null, null, false));
        assertThat(store.isPubliclyNamed()).isFalse();
        verify(history, org.mockito.Mockito.times(2)).event(StoreLogAction.STORE_PUBLIC_NAMING_CHANGED, STORE);
    }

    @Test
    void leavingPublicityAllowedClearsTheNameForAnyEditorWithAHistoryEntry() {
        store.setPubliclyNamed(true);
        when(rights.rights(any(), any())).thenReturn(editor(false, false));
        service.update(STORE, input(null, null, PublicityStatus.NOT_ALLOWED, null, null));
        assertThat(store.isPubliclyNamed()).isFalse();
        verify(history).event(StoreLogAction.STORE_PUBLIC_NAMING_CHANGED, STORE);
    }

    @Test
    void absentGroceriesStayAndAnEmptyListClearsThem() {
        when(rights.rights(any(), any())).thenReturn(editor(false, true));
        StoreGrocery bread = new StoreGrocery();
        bread.setStoreId(STORE);
        bread.setGroceryId(UUID.randomUUID());
        when(storeGroceries.findByStoreId(STORE)).thenReturn(List.of(bread));

        service.update(STORE, input(null, null, null, null, null));
        verify(storeGroceries, never()).delete(any());

        service.update(STORE, input(null, null, null, List.of(), null));
        verify(storeGroceries).delete(bread);
    }

    @Test
    void metadataIsForFoodsaversAndChainsOnlyForPeopleWhoMayKnowStores() {
        StoreVisibility visibility = mock(StoreVisibility.class);
        StoreChainRepository chains = mock(StoreChainRepository.class);
        StoreChain coop = new StoreChain();
        coop.setName("Coop");
        when(chains.findAllByOrderByNameAscIdAsc()).thenReturn(List.of(coop));
        StoreQueryService queries = new StoreQueryService(stores, memberships, mock(StoreCategoryRepository.class),
                chains, mock(GroceryRepository.class), storeGroceries, mock(PickupWeightCategoryRepository.class),
                service, rights, mock(StorePickupInfo.class), regions, currentActor, mock(PermissionService.class),
                visibility);

        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                Role.FOODSHARER, "newbie@local"));
        assertThat(codeOf(queries::metadata)).isEqualTo(ApiErrorCode.ACCESS_DENIED);

        SessionPrincipal foodsaver = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER,
                "foodsaver@local");
        when(currentActor.requirePrincipal()).thenReturn(foodsaver);
        when(visibility.mayKnowStores(foodsaver)).thenReturn(false);
        assertThat(queries.metadata().chains()).isEmpty();
        when(visibility.mayKnowStores(foodsaver)).thenReturn(true);
        assertThat(queries.metadata().chains()).containsExactly(coop);
    }
}
