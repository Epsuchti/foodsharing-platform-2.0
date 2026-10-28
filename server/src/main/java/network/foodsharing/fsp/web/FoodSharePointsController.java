package network.foodsharing.fsp.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.FoodSharePointsApi;
import network.foodsharing.api.model.Fsp;
import network.foodsharing.api.model.FspCreate;
import network.foodsharing.api.model.FspFollowUpdate;
import network.foodsharing.api.model.FspFollowing;
import network.foodsharing.api.model.FspManagersUpdate;
import network.foodsharing.api.model.FspStatusUpdate;
import network.foodsharing.api.model.FspSummary;
import network.foodsharing.api.model.FspUpdate;
import network.foodsharing.api.model.WallEntry;
import network.foodsharing.api.model.WallEntryCreate;
import network.foodsharing.api.model.WallEntryPage;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.domain.FspWallPost;
import network.foodsharing.fsp.mapper.FspMapper;
import network.foodsharing.fsp.service.FspQueryService;
import network.foodsharing.fsp.service.FspService;
import network.foodsharing.fsp.service.FspService.FspInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Food share points (FS1). */
@RestController
public class FoodSharePointsController implements FoodSharePointsApi {

    private final FspService service;
    private final FspQueryService queries;
    private final FspMapper mapper;

    public FoodSharePointsController(FspService service, FspQueryService queries, FspMapper mapper) {
        this.service = service;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Fsp> createFoodSharePoint(UUID regionId, FspCreate body) {
        FoodSharePoint fsp = service.create(regionId, new FspInput(body.getName(), body.getDescription(),
                body.getStreet(), body.getPostcode(), body.getCity(), body.getLat(), body.getLon(),
                body.getPhotoUploadId(), body.getManagerIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toFsp(queries.get(fsp.getId())));
    }

    @Override
    public ResponseEntity<WallEntry> createFoodSharePointWallPost(UUID fspId, WallEntryCreate body) {
        FspWallPost post = service.post(fspId, body.getBody());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toWallEntry(new FspQueryService.PostView(post, true)));
    }

    @Override
    public ResponseEntity<Void> deleteFoodSharePoint(UUID fspId) {
        service.delete(fspId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteFoodSharePointWallPost(UUID fspId, UUID postId) {
        service.deletePost(fspId, postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<FspFollowing> followFoodSharePoint(UUID fspId, FspFollowUpdate body) {
        return ResponseEntity.ok(mapper.toFollowing(service.follow(fspId, Boolean.TRUE.equals(body.getBell()),
                Boolean.TRUE.equals(body.getMail()))));
    }

    @Override
    public ResponseEntity<Fsp> getFoodSharePoint(UUID fspId) {
        return ResponseEntity.ok(mapper.toFsp(queries.get(fspId)));
    }

    @Override
    public ResponseEntity<WallEntryPage> listFoodSharePointWall(UUID fspId, Integer page, Integer size) {
        var result = queries.wall(fspId, page == null ? 0 : page, size == null ? 20 : size);
        return ResponseEntity.ok(new WallEntryPage().items(mapper.toWallEntries(result.getContent()))
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<List<FspSummary>> listMyFoodSharePoints() {
        return ResponseEntity.ok(mapper.toSummaries(queries.mine()));
    }

    @Override
    public ResponseEntity<List<FspSummary>> listRegionFoodSharePoints(UUID regionId, Boolean includeInactive) {
        return ResponseEntity.ok(mapper.toSummaries(queries.ofRegion(regionId,
                Boolean.TRUE.equals(includeInactive))));
    }

    @Override
    public ResponseEntity<Fsp> setFoodSharePointManagers(UUID fspId, FspManagersUpdate body) {
        service.setManagers(fspId, body.getUserIds());
        return ResponseEntity.ok(mapper.toFsp(queries.get(fspId)));
    }

    @Override
    public ResponseEntity<Fsp> setFoodSharePointStatus(UUID fspId, FspStatusUpdate body) {
        service.setStatus(fspId, body.getStatus() == null ? null : FspStatus.valueOf(body.getStatus().getValue()));
        return ResponseEntity.ok(mapper.toFsp(queries.get(fspId)));
    }

    @Override
    public ResponseEntity<Void> unfollowFoodSharePoint(UUID fspId) {
        service.unfollow(fspId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Fsp> updateFoodSharePoint(UUID fspId, FspUpdate body) {
        service.update(fspId, new FspInput(body.getName(), body.getDescription(), body.getStreet(),
                body.getPostcode(), body.getCity(), body.getLat(), body.getLon(), body.getPhotoUploadId(), null));
        return ResponseEntity.ok(mapper.toFsp(queries.get(fspId)));
    }
}
