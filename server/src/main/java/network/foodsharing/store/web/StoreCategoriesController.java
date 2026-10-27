package network.foodsharing.store.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StoreCategoriesApi;
import network.foodsharing.api.model.Grocery;
import network.foodsharing.api.model.MergeRequest;
import network.foodsharing.api.model.NamedItemUpdate;
import network.foodsharing.api.model.StoreCategory;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store category / grocery admin API (B7).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m3.md).
 */
@RestController
public class StoreCategoriesController implements StoreCategoriesApi {

    @Override
    public ResponseEntity<Grocery> createGrocery(NamedItemUpdate namedItemUpdate) {
        throw ApiException.notImplemented("createGrocery");
    }

    @Override
    public ResponseEntity<StoreCategory> createStoreCategory(NamedItemUpdate namedItemUpdate) {
        throw ApiException.notImplemented("createStoreCategory");
    }

    @Override
    public ResponseEntity<Void> deleteGrocery(UUID id) {
        throw ApiException.notImplemented("deleteGrocery");
    }

    @Override
    public ResponseEntity<Void> deleteStoreCategory(UUID id) {
        throw ApiException.notImplemented("deleteStoreCategory");
    }

    @Override
    public ResponseEntity<List<Grocery>> listGroceries() {
        throw ApiException.notImplemented("listGroceries");
    }

    @Override
    public ResponseEntity<List<StoreCategory>> listStoreCategories() {
        throw ApiException.notImplemented("listStoreCategories");
    }

    @Override
    public ResponseEntity<Grocery> mergeGrocery(UUID id, MergeRequest mergeRequest) {
        throw ApiException.notImplemented("mergeGrocery");
    }

    @Override
    public ResponseEntity<StoreCategory> mergeStoreCategory(UUID id, MergeRequest mergeRequest) {
        throw ApiException.notImplemented("mergeStoreCategory");
    }

    @Override
    public ResponseEntity<Grocery> updateGrocery(UUID id, NamedItemUpdate namedItemUpdate) {
        throw ApiException.notImplemented("updateGrocery");
    }

    @Override
    public ResponseEntity<StoreCategory> updateStoreCategory(UUID id, NamedItemUpdate namedItemUpdate) {
        throw ApiException.notImplemented("updateStoreCategory");
    }
}
