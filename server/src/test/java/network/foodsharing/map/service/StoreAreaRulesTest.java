package network.foodsharing.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.map.service.StoreAreaRules.Area;
import network.foodsharing.map.service.StoreAreaRules.Node;
import network.foodsharing.region.domain.UnitType;
import org.junit.jupiter.api.Test;

/** Decision 156, S17 D3. Non-established and deleted stores never reach the counts: see {@code MapIT}. */
class StoreAreaRulesTest {

    private final Map<UUID, Node> tree = new HashMap<>();
    private final UUID continent = node(null, UnitType.CONTINENT, "Europa", 50.0, 10.0);
    private final UUID country = node(continent, UnitType.COUNTRY, "Schweiz", 46.8, 8.2);
    private final UUID canton = node(country, UnitType.FEDERAL_STATE, "Zürich Kanton", 47.4, 8.6);
    private final UUID zurich = node(canton, UnitType.BIG_CITY, "Zürich", 47.37, 8.54);
    private final UUID wiedikon = node(zurich, UnitType.PART_OF_TOWN, "Wiedikon", 47.36, 8.52);
    private final UUID district = node(zurich, UnitType.DISTRICT, "Kreis 3", 47.35, 8.51);
    private final UUID winterthur = node(canton, UnitType.CITY, "Winterthur", 47.5, 8.72);

    private UUID node(UUID parent, UnitType type, String name, Double lat, Double lon) {
        UUID id = UUID.randomUUID();
        tree.put(id, new Node(id, parent, type, name, lat, lon));
        return id;
    }

    private List<Area> areas(Map<UUID, Long> counts) {
        return StoreAreaRules.areas(counts, tree);
    }

    @Test
    void aCountOfOneIsShownExactlyAtTheRegionsOwnPosition() {
        assertThat(areas(Map.of(winterthur, 1L))).containsExactly(new Area("Winterthur", 47.5, 8.72, 1));
    }

    @Test
    void partOfTownAndDistrictRollUpIntoTheirCity() {
        assertThat(areas(Map.of(zurich, 3L, wiedikon, 2L, district, 4L)))
                .containsExactly(new Area("Zürich", 47.37, 8.54, 9));
    }

    @Test
    void cityAndBigCityEachGetABubbleSortedByName() {
        assertThat(areas(Map.of(zurich, 16L, winterthur, 2L))).extracting(Area::name, Area::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Winterthur", 2),
                        org.assertj.core.groups.Tuple.tuple("Zürich", 16));
    }

    @Test
    void withoutACityAboveItCountsAtTheNearestCoarserRegionWithCoordinates() {
        UUID ruralDistrict = node(canton, UnitType.DISTRICT, "Bezirk Andelfingen", 47.6, 8.67);
        UUID region = node(canton, UnitType.REGION, "Zürcher Oberland", null, null);
        assertThat(areas(Map.of(ruralDistrict, 2L, region, 1L)))
                .containsExactly(new Area("Zürich Kanton", 47.4, 8.6, 3));
    }

    @Test
    void aCityWithoutCoordinatesCountsAtTheNextCoarserRegion() {
        UUID noPosition = node(canton, UnitType.CITY, "Uster", null, 8.7);
        UUID part = node(noPosition, UnitType.PART_OF_TOWN, "Nänikon", 47.37, 8.68);
        assertThat(areas(Map.of(noPosition, 1L, part, 1L))).containsExactly(new Area("Zürich Kanton", 47.4, 8.6, 2));
    }

    @Test
    void missingCoordinatesGoUpToCountry() {
        UUID state = node(country, UnitType.FEDERAL_STATE, "Glarus", null, null);
        UUID town = node(state, UnitType.CITY, "Glarus Stadt", null, null);
        assertThat(areas(Map.of(town, 5L))).containsExactly(new Area("Schweiz", 46.8, 8.2, 5));
    }

    @Test
    void noRegionWithCoordinatesUpToCountryMeansNoBubble() {
        UUID land = node(continent, UnitType.COUNTRY, "Atlantis", null, null);
        UUID town = node(land, UnitType.CITY, "Poseidonis", null, null);
        assertThat(areas(Map.of(town, 5L, land, 1L))).isEmpty();
    }

    @Test
    void aContinentOrWorkingGroupIsNeverABubble() {
        UUID group = node(winterthur, UnitType.WORKING_GROUP, "Arbeitsgruppe", 1.0, 1.0);
        assertThat(areas(Map.of(continent, 7L))).isEmpty();
        assertThat(areas(Map.of(group, 1L))).containsExactly(new Area("Winterthur", 47.5, 8.72, 1));
    }

    @Test
    void aRegionBelowACityStillCountsAtTheCity() {
        UUID below = node(zurich, UnitType.REGION, "Stadtregion", 47.0, 8.0);
        assertThat(areas(Map.of(below, 1L))).containsExactly(new Area("Zürich", 47.37, 8.54, 1));
    }

    @Test
    void everyStoreIsInOneBubble() {
        Map<UUID, Long> counts = Map.of(zurich, 1L, wiedikon, 2L, district, 3L, winterthur, 4L, canton, 5L, country, 6L);
        assertThat(areas(counts).stream().mapToInt(Area::count).sum()).isEqualTo(21);
    }

    @Test
    void anEmptyRegionHasNoBubble() {
        assertThat(areas(Map.of(zurich, 0L))).isEmpty();
    }
}
