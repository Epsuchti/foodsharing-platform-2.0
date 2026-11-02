package network.foodsharing.map.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.UnitType;

/**
 * Store counts per city for people who may not know the stores (decision 156, S17 D3). One bubble per CITY or
 * BIG_CITY with at least one store, with the exact count. PART_OF_TOWN and DISTRICT roll up into their city. A store
 * without a city at or above its region, or whose city has no coordinates, counts at the nearest coarser region with
 * coordinates, up to COUNTRY; with none it is in no bubble. The position is the region's own, never a store's.
 */
public final class StoreAreaRules {

    /** A region of the tree. */
    public record Node(UUID id, UUID parentId, UnitType type, String name, Double latitude, Double longitude) {

        boolean hasPosition() {
            return latitude != null && longitude != null;
        }
    }

    /** One bubble. */
    public record Area(String name, double latitude, double longitude, int count) {
    }

    private static final Set<UnitType> CITY_LEVEL = Set.of(UnitType.CITY, UnitType.BIG_CITY);
    private static final Set<UnitType> BUBBLE_TYPES = Set.of(UnitType.CITY, UnitType.BIG_CITY, UnitType.REGION,
            UnitType.FEDERAL_STATE, UnitType.COUNTRY);

    private StoreAreaRules() {
    }

    /**
     * @param countsByRegion stores per the region they are in
     * @param regions        the regions by id, at least those in the counts and their ancestors
     * @return the bubbles sorted by name
     */
    public static List<Area> areas(Map<UUID, Long> countsByRegion, Map<UUID, Node> regions) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        countsByRegion.forEach((regionId, count) -> bubbleRegion(regionId, regions)
                .ifPresent(node -> counts.merge(node.id(), count, Long::sum)));
        List<Area> areas = new ArrayList<>();
        counts.forEach((id, count) -> {
            Node node = regions.get(id);
            if (count > 0) {
                areas.add(new Area(node.name(), node.latitude(), node.longitude(), Math.toIntExact(count)));
            }
        });
        areas.sort(Comparator.comparing(Area::name));
        return areas;
    }

    /** The region whose bubble counts the stores of the given region. */
    static Optional<Node> bubbleRegion(UUID regionId, Map<UUID, Node> regions) {
        List<Node> chain = chain(regionId, regions);
        int city = 0;
        while (city < chain.size() && !CITY_LEVEL.contains(chain.get(city).type())) {
            city++;
        }
        List<Node> candidates = city < chain.size() ? chain.subList(city, chain.size()) : chain;
        return candidates.stream().filter(node -> BUBBLE_TYPES.contains(node.type()) && node.hasPosition())
                .findFirst();
    }

    /** The region and its ancestors, nearest first. */
    private static List<Node> chain(UUID regionId, Map<UUID, Node> regions) {
        List<Node> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Node node = regions.get(regionId);
        while (node != null && seen.add(node.id())) {
            chain.add(node);
            node = node.parentId() == null ? null : regions.get(node.parentId());
        }
        return chain;
    }
}
