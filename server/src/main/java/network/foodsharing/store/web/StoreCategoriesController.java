package network.foodsharing.store.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StoreCategoriesApi;
import network.foodsharing.api.model.Grocery;
import network.foodsharing.api.model.MergeRequest;
import network.foodsharing.api.model.NamedItemUpdate;
import network.foodsharing.api.model.StoreCategory;
import network.foodsharing.store.mapper.StoreMapper;
import network.foodsharing.store.service.StoreCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Store category / grocery admin API (B7, ORGA). */
@RestController
public class StoreCategoriesController implements StoreCategoriesApi {

    private final StoreCategoryService service;

    public StoreCategoriesController(StoreCategoryService service) {
        this.service = service;
    }

    private static StoreCategory category(StoreCategoryService.Item<network.foodsharing.store.domain.StoreCategory> i) {
        return StoreMapper.category(i.item(), i.storeCount());
    }

    private static Grocery grocery(StoreCategoryService.Item<network.foodsharing.store.domain.Grocery> i) {
        return StoreMapper.grocery(i.item(), i.storeCount());
    }

    @Override
    public ResponseEntity<Grocery> createGrocery(NamedItemUpdate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(grocery(service.createGrocery(body.getName())));
    }

    @Override
    public ResponseEntity<StoreCategory> createStoreCategory(NamedItemUpdate body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(category(service.createCategory(body.getName(), body.getType())));
    }

    @Override
    public ResponseEntity<Void> deleteGrocery(UUID id) {
        service.deleteGrocery(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteStoreCategory(UUID id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Grocery>> listGroceries() {
        return ResponseEntity.ok(service.listGroceries().stream().map(StoreCategoriesController::grocery).toList());
    }

    @Override
    public ResponseEntity<List<StoreCategory>> listStoreCategories() {
        return ResponseEntity.ok(service.listCategories().stream().map(StoreCategoriesController::category).toList());
    }

    @Override
    public ResponseEntity<Grocery> mergeGrocery(UUID id, MergeRequest body) {
        return ResponseEntity.ok(grocery(service.mergeGrocery(id, body.getTargetId())));
    }

    @Override
    public ResponseEntity<StoreCategory> mergeStoreCategory(UUID id, MergeRequest body) {
        return ResponseEntity.ok(category(service.mergeCategory(id, body.getTargetId())));
    }

    @Override
    public ResponseEntity<Grocery> updateGrocery(UUID id, NamedItemUpdate body) {
        return ResponseEntity.ok(grocery(service.updateGrocery(id, body.getName())));
    }

    @Override
    public ResponseEntity<StoreCategory> updateStoreCategory(UUID id, NamedItemUpdate body) {
        return ResponseEntity.ok(category(service.updateCategory(id, body.getName(), body.getType())));
    }
}
