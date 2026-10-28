package network.foodsharing.map.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PublicMapApi;
import network.foodsharing.api.model.MapBubble;
import network.foodsharing.api.model.MapMarker;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class PublicMapController implements PublicMapApi {

    @Override
    public ResponseEntity<MapBubble> getPublicMapBubble(String kind, UUID id) {
        throw ApiException.notImplemented("getPublicMapBubble");
    }

    @Override
    public ResponseEntity<List<MapMarker>> listPublicBasketMarkers() {
        throw ApiException.notImplemented("listPublicBasketMarkers");
    }

    @Override
    public ResponseEntity<List<MapMarker>> listPublicFspMarkers() {
        throw ApiException.notImplemented("listPublicFspMarkers");
    }

    @Override
    public ResponseEntity<List<MapMarker>> listPublicRegionMarkers() {
        throw ApiException.notImplemented("listPublicRegionMarkers");
    }
}
