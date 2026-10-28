package network.foodsharing.map.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.MapApi;
import network.foodsharing.api.model.GeoResult;
import network.foodsharing.api.model.MapBubble;
import network.foodsharing.api.model.MapMarker;
import network.foodsharing.api.model.StoreMarker;
import network.foodsharing.api.model.StoreMarkerHelp;
import network.foodsharing.api.model.StoreMarkerScope;
import network.foodsharing.api.model.StoreMarkerStatus;
import network.foodsharing.api.model.UserMarkerActivity;
import network.foodsharing.api.model.UserMarkerMember;
import network.foodsharing.api.model.UserMarkerRole;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class MapController implements MapApi {

    @Override
    public ResponseEntity<MapBubble> getMapBubble(String kind, UUID id) {
        throw ApiException.notImplemented("getMapBubble");
    }

    @Override
    public ResponseEntity<List<MapMarker>> listBasketMarkers() {
        throw ApiException.notImplemented("listBasketMarkers");
    }

    @Override
    public ResponseEntity<List<MapMarker>> listEventMarkers(OffsetDateTime from) {
        throw ApiException.notImplemented("listEventMarkers");
    }

    @Override
    public ResponseEntity<List<StoreMarker>> listStoreMarkers(StoreMarkerStatus status, StoreMarkerHelp help, StoreMarkerScope scope, UUID categoryId) {
        throw ApiException.notImplemented("listStoreMarkers");
    }

    @Override
    public ResponseEntity<List<MapMarker>> listUserMarkers(UUID regionId, UserMarkerRole role, UserMarkerActivity activity, UserMarkerMember member) {
        throw ApiException.notImplemented("listUserMarkers");
    }

    @Override
    public ResponseEntity<List<GeoResult>> searchMapLocation(String q) {
        throw ApiException.notImplemented("searchMapLocation");
    }
}
