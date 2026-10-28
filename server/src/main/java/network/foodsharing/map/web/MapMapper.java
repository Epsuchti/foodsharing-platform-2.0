package network.foodsharing.map.web;

import java.util.List;
import network.foodsharing.api.model.GeoResult;
import network.foodsharing.api.model.MapBubble;
import network.foodsharing.api.model.MapMarker;
import network.foodsharing.api.model.MapMarkerKind;
import network.foodsharing.api.model.StoreMarker;
import network.foodsharing.map.service.BubbleService;
import network.foodsharing.map.service.MapSearchService;
import network.foodsharing.map.service.MarkerService;

/** Service records → generated models (M1). */
final class MapMapper {

    private MapMapper() {
    }

    static List<MapMarker> markers(List<MarkerService.Marker> markers) {
        return markers.stream().map(m -> new MapMarker().id(m.id()).kind(MapMarkerKind.fromValue(m.kind().name()))
                .lat(m.lat()).lon(m.lon()).status(m.status())).toList();
    }

    static List<StoreMarker> stores(List<MarkerService.StoreMarker> markers) {
        return markers.stream().map(m -> new StoreMarker().id(m.id()).lat(m.lat()).lon(m.lon()).name(m.name())
                .cooperationStatus(m.cooperationStatus().name()).teamStatus(m.teamStatus().name())
                .categoryId(m.categoryId())).toList();
    }

    static MapBubble bubble(BubbleService.Bubble b) {
        return new MapBubble().title(b.title()).subtitle(b.subtitle()).imageUrl(b.imageUrl()).link(b.link())
                .lines(b.lines());
    }

    static List<GeoResult> hits(List<MapSearchService.GeoHit> hits) {
        return hits.stream().map(h -> new GeoResult().label(h.label()).lat(h.lat()).lon(h.lon())).toList();
    }
}
