package network.foodsharing.store.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store categories and groceries admin (plan §13.3 S4, ORGA): CRUD with unique names (409 NAME_TAKEN), delete only
 * when unused (409 CATEGORY_IN_USE), merge = move every store to the target and delete the source. History subject
 * SYSTEM with the item id / name in the metadata.
 */
@Service
public class StoreCategoryService {

    public record Item<T>(T item, long storeCount) {
    }

    private final StoreCategoryRepository categories;
    private final GroceryRepository groceries;
    private final StoreRepository stores;
    private final StoreGroceryRepository storeGroceries;
    private final CurrentActorService currentActor;
    private final HistoryService history;

    public StoreCategoryService(StoreCategoryRepository categories, GroceryRepository groceries,
                                StoreRepository stores, StoreGroceryRepository storeGroceries,
                                CurrentActorService currentActor, HistoryService history) {
        this.categories = categories;
        this.groceries = groceries;
        this.stores = stores;
        this.storeGroceries = storeGroceries;
        this.currentActor = currentActor;
        this.history = history;
    }

    // ---- categories ----

    @Transactional(readOnly = true)
    public List<Item<StoreCategory>> listCategories() {
        currentActor.requireRole(Role.ORGA);
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new Item<>(c, stores.countByCategoryId(c.getId()))).toList();
    }

    /** @throws ApiException 403, 400 VALIDATION_FAILED, 409 NAME_TAKEN */
    @Transactional
    public Item<StoreCategory> createCategory(String name, String type) {
        currentActor.requireRole(Role.ORGA);
        String clean = name(name);
        categories.findByNameIgnoreCase(clean).ifPresent(c -> {
            throw ApiException.conflict(ApiErrorCode.NAME_TAKEN);
        });
        StoreCategory category = new StoreCategory();
        category.setName(clean);
        if (type != null && !type.isBlank()) {
            category.setType(type.strip());
        }
        categories.save(category);
        record(StoreCategoryAction.CATEGORY_CREATED, category.getId(), clean, null);
        return new Item<>(category, 0);
    }

    /** @throws ApiException 403, 404 CATEGORY_NOT_FOUND, 400, 409 NAME_TAKEN */
    @Transactional
    public Item<StoreCategory> updateCategory(UUID id, String name, String type) {
        currentActor.requireRole(Role.ORGA);
        StoreCategory category = requireCategory(id);
        String clean = name(name);
        categories.findByNameIgnoreCase(clean).filter(c -> !c.getId().equals(id)).ifPresent(c -> {
            throw ApiException.conflict(ApiErrorCode.NAME_TAKEN);
        });
        String previous = category.getName();
        category.setName(clean);
        if (type != null && !type.isBlank()) {
            category.setType(type.strip());
        }
        history.record(history.event(StoreCategoryAction.CATEGORY_UPDATED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata("id", id.toString()).change("name", previous, clean).build());
        return new Item<>(category, stores.countByCategoryId(id));
    }

    /** @throws ApiException 403, 404 CATEGORY_NOT_FOUND, 409 CATEGORY_IN_USE */
    @Transactional
    public void deleteCategory(UUID id) {
        currentActor.requireRole(Role.ORGA);
        StoreCategory category = requireCategory(id);
        if (stores.countByCategoryId(id) > 0) {
            throw ApiException.conflict(ApiErrorCode.CATEGORY_IN_USE);
        }
        categories.delete(category);
        record(StoreCategoryAction.CATEGORY_DELETED, id, category.getName(), null);
    }

    /** Moves every store (also deleted ones) to the target, deletes the source. @throws ApiException 403, 404, 400 */
    @Transactional
    public Item<StoreCategory> mergeCategory(UUID sourceId, UUID targetId) {
        currentActor.requireRole(Role.ORGA);
        StoreCategory source = requireCategory(sourceId);
        if (targetId == null || targetId.equals(sourceId)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        StoreCategory target = requireCategory(targetId);
        List<Store> moved = stores.findByCategoryId(sourceId);
        moved.forEach(s -> s.setCategoryId(targetId));
        stores.flush();
        categories.delete(source);
        history.record(history.event(StoreCategoryAction.CATEGORY_MERGED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata("id", sourceId.toString()).metadata("name", source.getName())
                .metadata("targetId", targetId.toString()).metadata("targetName", target.getName())
                .metadata("stores", moved.size()).build());
        return new Item<>(target, stores.countByCategoryId(targetId));
    }

    // ---- groceries ----

    /** Names of the given groceries (unknown ids are left out); no permission needed (baskets). */
    @Transactional(readOnly = true)
    public Map<UUID, String> groceryNames(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return groceries.findAllById(ids).stream().collect(Collectors.toMap(Grocery::getId, Grocery::getName));
    }

    /**
     * All groceries (id → name, sorted by name) without a permission check: the basket form offers them to every
     * signed-in user (B1); the caller checks access.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> groceryCatalog() {
        Map<UUID, String> catalog = new java.util.LinkedHashMap<>();
        groceries.findAllByOrderByNameAsc().forEach(g -> catalog.put(g.getId(), g.getName()));
        return catalog;
    }

    @Transactional(readOnly = true)
    public List<Item<Grocery>> listGroceries() {
        currentActor.requireRole(Role.ORGA);
        return groceries.findAllByOrderByNameAsc().stream()
                .map(g -> new Item<>(g, storeGroceries.countByGroceryId(g.getId()))).toList();
    }

    /** @throws ApiException 403, 400 VALIDATION_FAILED, 409 NAME_TAKEN */
    @Transactional
    public Item<Grocery> createGrocery(String name) {
        currentActor.requireRole(Role.ORGA);
        String clean = name(name);
        groceries.findByNameIgnoreCase(clean).ifPresent(g -> {
            throw ApiException.conflict(ApiErrorCode.NAME_TAKEN);
        });
        Grocery grocery = new Grocery();
        grocery.setName(clean);
        groceries.save(grocery);
        record(StoreCategoryAction.GROCERY_CREATED, grocery.getId(), clean, null);
        return new Item<>(grocery, 0);
    }

    /** @throws ApiException 403, 404 GROCERY_NOT_FOUND, 400, 409 NAME_TAKEN */
    @Transactional
    public Item<Grocery> updateGrocery(UUID id, String name) {
        currentActor.requireRole(Role.ORGA);
        Grocery grocery = requireGrocery(id);
        String clean = name(name);
        groceries.findByNameIgnoreCase(clean).filter(g -> !g.getId().equals(id)).ifPresent(g -> {
            throw ApiException.conflict(ApiErrorCode.NAME_TAKEN);
        });
        String previous = grocery.getName();
        grocery.setName(clean);
        history.record(history.event(StoreCategoryAction.GROCERY_UPDATED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata("id", id.toString()).change("name", previous, clean).build());
        return new Item<>(grocery, storeGroceries.countByGroceryId(id));
    }

    /** @throws ApiException 403, 404 GROCERY_NOT_FOUND, 409 CATEGORY_IN_USE */
    @Transactional
    public void deleteGrocery(UUID id) {
        currentActor.requireRole(Role.ORGA);
        Grocery grocery = requireGrocery(id);
        if (storeGroceries.countByGroceryId(id) > 0) {
            throw ApiException.conflict(ApiErrorCode.CATEGORY_IN_USE);
        }
        groceries.delete(grocery);
        record(StoreCategoryAction.GROCERY_DELETED, id, grocery.getName(), null);
    }

    /** Moves every store link to the target (no duplicates), deletes the source. @throws ApiException 403, 404, 400 */
    @Transactional
    public Item<Grocery> mergeGrocery(UUID sourceId, UUID targetId) {
        currentActor.requireRole(Role.ORGA);
        Grocery source = requireGrocery(sourceId);
        if (targetId == null || targetId.equals(sourceId)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Grocery target = requireGrocery(targetId);
        Set<UUID> haveTarget = storeGroceries.findByGroceryId(targetId).stream().map(StoreGrocery::getStoreId)
                .collect(Collectors.toSet());
        List<StoreGrocery> links = storeGroceries.findByGroceryId(sourceId);
        for (StoreGrocery link : links) {
            storeGroceries.delete(link);
            if (!haveTarget.contains(link.getStoreId())) {
                StoreGrocery moved = new StoreGrocery();
                moved.setStoreId(link.getStoreId());
                moved.setGroceryId(targetId);
                storeGroceries.save(moved);
            }
        }
        storeGroceries.flush();
        groceries.delete(source);
        history.record(history.event(StoreCategoryAction.GROCERY_MERGED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("id", sourceId.toString(), "name", source.getName(), "targetId",
                        targetId.toString(), "targetName", target.getName(), "stores", links.size()))
                .build());
        return new Item<>(target, storeGroceries.countByGroceryId(targetId));
    }

    private StoreCategory requireCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.CATEGORY_NOT_FOUND));
    }

    private Grocery requireGrocery(UUID id) {
        return groceries.findById(id).orElseThrow(() -> ApiException.notFound(ApiErrorCode.GROCERY_NOT_FOUND));
    }

    private void record(StoreCategoryAction action, UUID id, String name, String reason) {
        history.record(history.event(action, HistoryService.SYSTEM_SUBJECT_ID).metadata("id", id.toString())
                .metadata("name", name).reason(reason).build());
    }

    private static String name(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 200) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        return name.strip();
    }
}
