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
import network.foodsharing.map.service.BubbleService;
import network.foodsharing.map.service.MapSearchService;
import network.foodsharing.map.service.MarkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Map for signed-in users (M1). */
@RestController
public class MapController implements MapApi {

    private final MarkerService markers;
    private final BubbleService bubbles;
    private final MapSearchService search;

    public MapController(MarkerService markers, BubbleService bubbles, MapSearchService search) {
        this.markers = markers;
        this.bubbles = bubbles;
        this.search = search;
    }

    private static <E extends Enum<E>> E convert(Enum<?> value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value.name());
    }

    @Override
    public ResponseEntity<MapBubble> getMapBubble(String kind, UUID id) {
        return ResponseEntity.ok(MapMapper.bubble(bubbles.bubble(kind, id, false)));
    }

    @Override
    public ResponseEntity<List<MapMarker>> listBasketMarkers() {
        return ResponseEntity.ok(MapMapper.markers(markers.baskets()));
    }

    @Override
    public ResponseEntity<List<MapMarker>> listEventMarkers(OffsetDateTime from) {
        return ResponseEntity.ok(MapMapper.markers(markers.events(from == null ? null : from.toInstant())));
    }

    @Override
    public ResponseEntity<List<StoreMarker>> listStoreMarkers(StoreMarkerStatus status, StoreMarkerHelp help,
                                                              StoreMarkerScope scope, UUID categoryId) {
        return ResponseEntity.ok(MapMapper.stores(markers.stores(
                convert(status, network.foodsharing.map.domain.StoreMarkerStatus.class),
                convert(help, network.foodsharing.map.domain.StoreMarkerHelp.class),
                convert(scope, network.foodsharing.map.domain.StoreMarkerScope.class), categoryId)));
    }

    @Override
    public ResponseEntity<List<MapMarker>> listUserMarkers(UUID regionId, UserMarkerRole role,
                                                           UserMarkerActivity activity, UserMarkerMember member) {
        return ResponseEntity.ok(MapMapper.markers(markers.users(regionId,
                convert(role, network.foodsharing.map.domain.UserMarkerRole.class),
                convert(activity, network.foodsharing.map.domain.UserMarkerActivity.class),
                convert(member, network.foodsharing.map.domain.UserMarkerMember.class))));
    }

    @Override
    public ResponseEntity<List<GeoResult>> searchMapLocation(String q) {
        return ResponseEntity.ok(MapMapper.hits(search.search(q)));
    }
}
