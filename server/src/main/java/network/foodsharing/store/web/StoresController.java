package network.foodsharing.store.web;

import org.springframework.http.HttpStatus;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.store.mapper.StoreMapper;
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

    private final StoreService stores;
    private final StoreQueryService queries;
    private final StoreMapper mapper;

    public StoresController(StoreService stores, StoreQueryService queries, StoreMapper mapper) {
        this.stores = stores;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<WallPost> addWallPost(UUID storeId, WallPostCreate wallPostCreate) {
        throw ApiException.notImplemented("addWallPost");
    }

    @Override
    public ResponseEntity<StoreDetail> createStore(UUID regionId, StoreCreate storeCreate) {
        var store = stores.create(regionId, mapper.toInput(storeCreate));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDetail(queries.detail(store.getId())));
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
        return ResponseEntity.ok(mapper.toDetail(queries.detail(storeId)));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getStoreLog(UUID storeId, OffsetDateTime from, OffsetDateTime to, List<String> actions, Integer page, Integer size) {
        throw ApiException.notImplemented("getStoreLog");
    }

    @Override
    public ResponseEntity<StoreMetadata> getStoreMetadata() {
        return ResponseEntity.ok(mapper.toMetadata(queries.metadata()));
    }

    @Override
    public ResponseEntity<StorePermissions> getStorePermissions(UUID storeId) {
        return ResponseEntity.ok(mapper.toPermissions(queries.permissionsOf(storeId)));
    }

    @Override
    public ResponseEntity<StoreStatistics> getStoreStatistics(UUID storeId, LocalDate from, LocalDate to) {
        throw ApiException.notImplemented("getStoreStatistics");
    }

    @Override
    public ResponseEntity<List<StoreListItem>> listMyStores(Boolean excludeInactive) {
        return ResponseEntity.ok(queries.myStores(Boolean.TRUE.equals(excludeInactive)).stream().map(mapper::toListItem).toList());
    }

    @Override
    public ResponseEntity<StoreListPage> listRegionStores(UUID regionId, List<CooperationStatus> cooperationStatus, List<TeamSearchStatus> teamStatus, String search, Boolean includeSubregions, Integer page, Integer size) {
        var result = queries.regionStores(regionId,
                cooperationStatus == null ? null : cooperationStatus.stream().map(s -> network.foodsharing.store.domain.CooperationStatus.valueOf(s.name())).toList(),
                teamStatus == null ? null : teamStatus.stream().map(s -> network.foodsharing.store.domain.TeamSearchStatus.valueOf(s.name())).toList(),
                search, Boolean.TRUE.equals(includeSubregions), page, size);
        StoreListPage model = new StoreListPage();
        model.setItems(result.getContent().stream().map(mapper::toListItem).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<WallPostPage> listWallPosts(UUID storeId, Integer page, Integer size) {
        throw ApiException.notImplemented("listWallPosts");
    }

    @Override
    public ResponseEntity<StoreDetail> updateStore(UUID storeId, StorePatch storePatch) {
        stores.update(storeId, mapper.toInput(storePatch));
        return ResponseEntity.ok(mapper.toDetail(queries.detail(storeId)));
    }
}
