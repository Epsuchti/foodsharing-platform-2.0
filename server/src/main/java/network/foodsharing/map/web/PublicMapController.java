package network.foodsharing.map.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PublicMapApi;
import network.foodsharing.api.model.MapBubble;
import network.foodsharing.api.model.MapMarker;
import network.foodsharing.map.service.BubbleService;
import network.foodsharing.map.service.MarkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public map layers (M1): FSPs, regions, baskets (rounded) and their bubbles. */
@RestController
public class PublicMapController implements PublicMapApi {

    private final MarkerService markers;
    private final BubbleService bubbles;

    public PublicMapController(MarkerService markers, BubbleService bubbles) {
        this.markers = markers;
        this.bubbles = bubbles;
    }

    @Override
    public ResponseEntity<MapBubble> getPublicMapBubble(String kind, UUID id) {
        return ResponseEntity.ok(MapMapper.bubble(bubbles.bubble(kind, id, true)));
    }

    @Override
    public ResponseEntity<List<MapMarker>> listPublicBasketMarkers() {
        return ResponseEntity.ok(MapMapper.markers(markers.baskets()));
    }

    @Override
    public ResponseEntity<List<MapMarker>> listPublicFspMarkers() {
        return ResponseEntity.ok(MapMapper.markers(markers.fsps()));
    }

    @Override
    public ResponseEntity<List<MapMarker>> listPublicRegionMarkers() {
        return ResponseEntity.ok(MapMapper.markers(markers.regions()));
    }
}
