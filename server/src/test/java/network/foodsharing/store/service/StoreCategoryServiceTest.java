package network.foodsharing.store.service;

import static network.foodsharing.store.service.StoreServiceTest.codeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Grocery;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreCategory;
import network.foodsharing.store.domain.StoreCategoryAction;
import network.foodsharing.store.domain.StoreGrocery;
import network.foodsharing.store.repository.GroceryRepository;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreGroceryRepository;
import network.foodsharing.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Category / grocery admin: ORGA only, unique names, in-use guard, merge moves stores and deletes the source. */
class StoreCategoryServiceTest {

    StoreCategoryRepository categories = mock(StoreCategoryRepository.class);
    GroceryRepository groceries = mock(GroceryRepository.class);
    StoreRepository stores = mock(StoreRepository.class);
    StoreGroceryRepository storeGroceries = mock(StoreGroceryRepository.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    StoreCategoryService service = new StoreCategoryService(categories, groceries, stores, storeGroceries,
            currentActor, history);
    StoreCategory bakery = category("Bäckerei");
    StoreCategory market = category("Markt");
    Grocery bread = grocery("Brot");
    Grocery pastry = grocery("Gebäck");

    static StoreCategory category(String name) {
        StoreCategory c = new StoreCategory();
        c.setId(UUID.randomUUID());
        c.setName(name);
        return c;
    }

    static Grocery grocery(String name) {
        Grocery g = new Grocery();
        g.setId(UUID.randomUUID());
        g.setName(name);
        return g;
    }

    @BeforeEach
    void setUp() {
        for (StoreCategory c : List.of(bakery, market)) {
            when(categories.findById(c.getId())).thenReturn(Optional.of(c));
        }
        for (Grocery g : List.of(bread, pastry)) {
            when(groceries.findById(g.getId())).thenReturn(Optional.of(g));
        }
        when(categories.findByNameIgnoreCase("bäckerei")).thenReturn(Optional.of(bakery));
        when(categories.save(any())).thenAnswer(i -> {
            StoreCategory c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
    }

    @Test
    void everythingNeedsOrga() {
        when(currentActor.requireRole(network.foodsharing.api.model.Role.ORGA))
                .thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        assertThat(codeOf(() -> service.createCategory("Kiosk", null))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(() -> service.updateCategory(bakery.getId(), "Bäcker", null)))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(() -> service.deleteCategory(bakery.getId()))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(service::listGroceries)).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(() -> service.createGrocery("Obst"))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(codeOf(() -> service.mergeCategory(bakery.getId(), market.getId())))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void everySignedInUserReadsCategoryNamesButOnlyOrgaSeesUsageCounts() {
        when(categories.findAllByOrderByNameAscIdAsc()).thenReturn(List.of(bakery, market));
        when(stores.countByCategoryId(bakery.getId())).thenReturn(3L);
        when(currentActor.requireRole(network.foodsharing.api.model.Role.ORGA))
                .thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        when(currentActor.isOrga()).thenReturn(false);
        assertThat(service.listCategories()).extracting(i -> i.item().getName(), StoreCategoryService.Item::storeCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Bäckerei", null),
                        org.assertj.core.groups.Tuple.tuple("Markt", null));
        verify(stores, never()).countByCategoryId(any());

        when(currentActor.isOrga()).thenReturn(true);
        assertThat(service.listCategories()).extracting(StoreCategoryService.Item::storeCount)
                .containsExactly(3L, 0L);

        when(currentActor.requirePrincipal()).thenThrow(ApiException.unauthenticated());
        assertThat(codeOf(service::listCategories)).isEqualTo(ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void namesAreUniqueIgnoringCase() {
        assertThat(codeOf(() -> service.createCategory("bäckerei", null))).isEqualTo(ApiErrorCode.NAME_TAKEN);
        assertThat(codeOf(() -> service.updateCategory(market.getId(), "bäckerei", null)))
                .isEqualTo(ApiErrorCode.NAME_TAKEN);
        assertThat(codeOf(() -> service.createCategory(" ", null))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        var created = service.createCategory(" Kiosk ", "PICKUP");
        assertThat(created.item().getName()).isEqualTo("Kiosk");
        verify(history).event(StoreCategoryAction.CATEGORY_CREATED, HistoryService.SYSTEM_SUBJECT_ID);
        service.updateCategory(bakery.getId(), "Bäckerei", null); // own name is fine
    }

    @Test
    void deleteOnlyWhenUnused() {
        when(stores.countByCategoryId(bakery.getId())).thenReturn(2L);
        assertThat(codeOf(() -> service.deleteCategory(bakery.getId()))).isEqualTo(ApiErrorCode.CATEGORY_IN_USE);
        verify(categories, never()).delete(any());
        service.deleteCategory(market.getId());
        verify(categories).delete(market);
        when(storeGroceries.countByGroceryId(bread.getId())).thenReturn(1L);
        assertThat(codeOf(() -> service.deleteGrocery(bread.getId()))).isEqualTo(ApiErrorCode.CATEGORY_IN_USE);
        assertThat(codeOf(() -> service.deleteGrocery(UUID.randomUUID()))).isEqualTo(ApiErrorCode.GROCERY_NOT_FOUND);
    }

    @Test
    void categoryMergeMovesStoresAndDeletesSource() {
        Store a = new Store();
        Store b = new Store();
        a.setCategoryId(bakery.getId());
        b.setCategoryId(bakery.getId());
        when(stores.findByCategoryId(bakery.getId())).thenReturn(List.of(a, b));
        service.mergeCategory(bakery.getId(), market.getId());
        assertThat(a.getCategoryId()).isEqualTo(market.getId());
        assertThat(b.getCategoryId()).isEqualTo(market.getId());
        verify(categories).delete(bakery);
        verify(history).event(StoreCategoryAction.CATEGORY_MERGED, HistoryService.SYSTEM_SUBJECT_ID);
        assertThat(codeOf(() -> service.mergeCategory(market.getId(), market.getId())))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(() -> service.mergeCategory(market.getId(), UUID.randomUUID())))
                .isEqualTo(ApiErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void groceryMergeMovesLinksWithoutDuplicates() {
        UUID storeWithBoth = UUID.randomUUID();
        UUID storeWithSource = UUID.randomUUID();
        when(storeGroceries.findByGroceryId(pastry.getId())).thenReturn(List.of(link(storeWithBoth, pastry)));
        StoreGrocery dup = link(storeWithBoth, bread);
        StoreGrocery moved = link(storeWithSource, bread);
        when(storeGroceries.findByGroceryId(bread.getId())).thenReturn(List.of(dup, moved));
        service.mergeGrocery(bread.getId(), pastry.getId());
        verify(storeGroceries).delete(dup);
        verify(storeGroceries).delete(moved);
        ArgumentCaptor<StoreGrocery> saved = ArgumentCaptor.forClass(StoreGrocery.class);
        verify(storeGroceries).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo(storeWithSource);
        assertThat(saved.getValue().getGroceryId()).isEqualTo(pastry.getId());
        verify(groceries).delete(bread);
        verify(history).event(StoreCategoryAction.GROCERY_MERGED, HistoryService.SYSTEM_SUBJECT_ID);
    }

    static StoreGrocery link(UUID storeId, Grocery grocery) {
        StoreGrocery g = new StoreGrocery();
        g.setStoreId(storeId);
        g.setGroceryId(grocery.getId());
        return g;
    }
}
