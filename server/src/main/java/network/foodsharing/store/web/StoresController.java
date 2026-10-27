package network.foodsharing.store.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StoresApi;
import network.foodsharing.api.model.CooperationStatus;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.ReasonBody;
import network.foodsharing.api.model.StoreCreate;
import network.foodsharing.api.model.StoreDetail;
import network.foodsharing.api.model.StoreListItem;
import network.foodsharing.api.model.StoreListPage;
import network.foodsharing.api.model.StoreMetadata;
import network.foodsharing.api.model.StorePatch;
import network.foodsharing.api.model.StorePermissions;
import network.foodsharing.api.model.StoreStatistics;
import network.foodsharing.api.model.TeamSearchStatus;
import network.foodsharing.api.model.WallPost;
import network.foodsharing.api.model.WallPostCreate;
import network.foodsharing.api.model.WallPostPage;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store API (B2, B6).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m3.md).
 */
@RestController
public class StoresController implements StoresApi {

    @Override
    public ResponseEntity<WallPost> addWallPost(UUID storeId, WallPostCreate wallPostCreate) {
        throw ApiException.notImplemented("addWallPost");
    }

    @Override
    public ResponseEntity<StoreDetail> createStore(UUID regionId, StoreCreate storeCreate) {
        throw ApiException.notImplemented("createStore");
    }

    @Override
    public ResponseEntity<Void> deleteStore(UUID storeId, ReasonBody reasonBody) {
        throw ApiException.notImplemented("deleteStore");
    }

    @Override
    public ResponseEntity<Void> deleteWallPost(UUID postId, UUID storeId) {
        throw ApiException.notImplemented("deleteWallPost");
    }

    @Override
    public ResponseEntity<StoreDetail> getStore(UUID storeId) {
        throw ApiException.notImplemented("getStore");
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getStoreLog(UUID storeId, OffsetDateTime from, OffsetDateTime to, List<String> actions, Integer page, Integer size) {
        throw ApiException.notImplemented("getStoreLog");
    }

    @Override
    public ResponseEntity<StoreMetadata> getStoreMetadata() {
        throw ApiException.notImplemented("getStoreMetadata");
    }

    @Override
    public ResponseEntity<StorePermissions> getStorePermissions(UUID storeId) {
        throw ApiException.notImplemented("getStorePermissions");
    }

    @Override
    public ResponseEntity<StoreStatistics> getStoreStatistics(UUID storeId, LocalDate from, LocalDate to) {
        throw ApiException.notImplemented("getStoreStatistics");
    }

    @Override
    public ResponseEntity<List<StoreListItem>> listMyStores(Boolean excludeInactive) {
        throw ApiException.notImplemented("listMyStores");
    }

    @Override
    public ResponseEntity<StoreListPage> listRegionStores(UUID regionId, List<CooperationStatus> cooperationStatus, List<TeamSearchStatus> teamStatus, String search, Boolean includeSubregions, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionStores");
    }

    @Override
    public ResponseEntity<WallPostPage> listWallPosts(UUID storeId, Integer page, Integer size) {
        throw ApiException.notImplemented("listWallPosts");
    }

    @Override
    public ResponseEntity<StoreDetail> updateStore(UUID storeId, StorePatch storePatch) {
        throw ApiException.notImplemented("updateStore");
    }
}
