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
import network.foodsharing.api.model.WallPost1;
import network.foodsharing.api.model.WallPostCreate1;
import network.foodsharing.api.model.WallPostPage1;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class FoodSharePointsController implements FoodSharePointsApi {

    @Override
    public ResponseEntity<Fsp> createFoodSharePoint(UUID regionId, FspCreate fspCreate) {
        throw ApiException.notImplemented("createFoodSharePoint");
    }

    @Override
    public ResponseEntity<WallPost1> createFoodSharePointWallPost(UUID fspId, WallPostCreate1 wallPostCreate1) {
        throw ApiException.notImplemented("createFoodSharePointWallPost");
    }

    @Override
    public ResponseEntity<Void> deleteFoodSharePoint(UUID fspId) {
        throw ApiException.notImplemented("deleteFoodSharePoint");
    }

    @Override
    public ResponseEntity<Void> deleteFoodSharePointWallPost(UUID fspId, UUID postId) {
        throw ApiException.notImplemented("deleteFoodSharePointWallPost");
    }

    @Override
    public ResponseEntity<FspFollowing> followFoodSharePoint(UUID fspId, FspFollowUpdate fspFollowUpdate) {
        throw ApiException.notImplemented("followFoodSharePoint");
    }

    @Override
    public ResponseEntity<Fsp> getFoodSharePoint(UUID fspId) {
        throw ApiException.notImplemented("getFoodSharePoint");
    }

    @Override
    public ResponseEntity<WallPostPage1> listFoodSharePointWall(UUID fspId, Integer page, Integer size) {
        throw ApiException.notImplemented("listFoodSharePointWall");
    }

    @Override
    public ResponseEntity<List<FspSummary>> listMyFoodSharePoints() {
        throw ApiException.notImplemented("listMyFoodSharePoints");
    }

    @Override
    public ResponseEntity<List<FspSummary>> listRegionFoodSharePoints(UUID regionId, Boolean includeInactive) {
        throw ApiException.notImplemented("listRegionFoodSharePoints");
    }

    @Override
    public ResponseEntity<Fsp> setFoodSharePointManagers(UUID fspId, FspManagersUpdate fspManagersUpdate) {
        throw ApiException.notImplemented("setFoodSharePointManagers");
    }

    @Override
    public ResponseEntity<Fsp> setFoodSharePointStatus(UUID fspId, FspStatusUpdate fspStatusUpdate) {
        throw ApiException.notImplemented("setFoodSharePointStatus");
    }

    @Override
    public ResponseEntity<Void> unfollowFoodSharePoint(UUID fspId) {
        throw ApiException.notImplemented("unfollowFoodSharePoint");
    }

    @Override
    public ResponseEntity<Fsp> updateFoodSharePoint(UUID fspId, FspUpdate fspUpdate) {
        throw ApiException.notImplemented("updateFoodSharePoint");
    }
}
