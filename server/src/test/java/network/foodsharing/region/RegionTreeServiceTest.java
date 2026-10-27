package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionTreeService;
import network.foodsharing.testsupport.JpaSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Closure table maintenance: insert, move (incl. subtree), refusal to move into the own subtree. */
@JpaSliceTest
@Import({RegionTreeService.class, RegionService.class, RegionClosureRepository.class})
class RegionTreeServiceTest {

    @Autowired
    private RegionTreeService tree;

    @Autowired
    private RegionService regionService;

    @Autowired
    private RegionRepository regions;

    @Autowired
    private RegionClosureRepository closure;

    @Autowired
    private HistoryEntryRepository history;

    @Autowired
    private JdbcTemplate jdbc;

    private Region root;
    private Region a;
    private Region a1;
    private Region a1x;
    private Region b;

    @BeforeEach
    void setUp() {
        root = create("root", UnitType.COUNTRY, null);
        a = create("a", UnitType.FEDERAL_STATE, root);
        a1 = create("a1", UnitType.CITY, a);
        a1x = create("a1x", UnitType.PART_OF_TOWN, a1);
        b = create("b", UnitType.FEDERAL_STATE, root);
    }

    private Region create(String slug, UnitType type, Region parent) {
        Region region = new Region();
        region.setSlug(slug);
        region.setName(slug.toUpperCase());
        region.setType(type);
        region.setParentId(parent == null ? null : parent.getId());
        Region saved = regions.saveAndFlush(region);
        tree.attach(saved);
        return saved;
    }

    @Test
    void attachWritesSelfAndAncestorRows() {
        assertThat(closure.ancestors(a1x.getId())).containsExactly(root.getId(), a.getId(), a1.getId(), a1x.getId());
        assertThat(closure.subtree(a.getId())).containsExactlyInAnyOrder(a.getId(), a1.getId(), a1x.getId());
        assertThat(jdbc.queryForObject("select depth from region_closure where region_id = ? and ancestor_id = ?",
                Integer.class, a1x.getId(), root.getId())).isEqualTo(3);
        assertThat(closure.count()).isEqualTo(1 + 2 + 3 + 4 + 2);
        assertThat(regions.findById(a.getId()).orElseThrow().isHasChildren()).isTrue();
        assertThat(regions.findById(b.getId()).orElseThrow().isHasChildren()).isFalse();
        assertThat(regionService.ancestors(a1x.getId())).extracting(Region::getSlug)
                .containsExactly("root", "a", "a1", "a1x");
    }

    @Test
    void moveReattachesTheWholeSubtree() {
        tree.move(a1.getId(), b.getId(), "reorganised");

        assertThat(closure.ancestors(a1x.getId())).containsExactly(root.getId(), b.getId(), a1.getId(), a1x.getId());
        assertThat(closure.subtree(a.getId())).containsExactly(a.getId());
        assertThat(closure.subtree(b.getId())).containsExactlyInAnyOrder(b.getId(), a1.getId(), a1x.getId());
        assertThat(jdbc.queryForObject("select depth from region_closure where region_id = ? and ancestor_id = ?",
                Integer.class, a1x.getId(), b.getId())).isEqualTo(2);
        assertThat(regions.findById(a1.getId()).orElseThrow().getParentId()).isEqualTo(b.getId());
        assertThat(regions.findById(a.getId()).orElseThrow().isHasChildren()).isFalse();
        assertThat(regions.findById(b.getId()).orElseThrow().isHasChildren()).isTrue();
        assertThat(closure.count()).isEqualTo(1 + 2 + 2 + 3 + 4);

        List<HistoryEntry> moved = history.findAll().stream().filter(entry -> entry.getAction().equals("MOVED")).toList();
        assertThat(moved).singleElement().satisfies(entry -> {
            assertThat(entry.getSubjectId()).isEqualTo(a1.getId());
            assertThat(entry.getField()).isEqualTo("parentId");
            assertThat(entry.getOldValue()).isEqualTo(a.getId().toString());
            assertThat(entry.getNewValue()).isEqualTo(b.getId().toString());
            assertThat(entry.getReason()).isEqualTo("reorganised");
        });
    }

    @Test
    void movingIntoTheOwnSubtreeOrOntoItselfIsRefused() {
        for (UUID target : List.of(a1x.getId(), a1.getId(), a.getId())) {
            assertThatThrownBy(() -> tree.move(a.getId(), target, null))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.CANNOT_MOVE_INTO_DESCENDANT));
        }
        assertThat(closure.ancestors(a1x.getId())).containsExactly(root.getId(), a.getId(), a1.getId(), a1x.getId());
    }

    @Test
    void unknownRegionsAre404() {
        assertThatThrownBy(() -> tree.move(RegionFixtures.unknownId(), b.getId(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_FOUND));
        assertThatThrownBy(() -> tree.move(a.getId(), RegionFixtures.unknownId(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_FOUND));
    }

    @Test
    void movingToTheRootAndBack() {
        tree.move(a.getId(), null, null);
        assertThat(closure.ancestors(a1x.getId())).containsExactly(a.getId(), a1.getId(), a1x.getId());
        assertThat(regions.findById(root.getId()).orElseThrow().isHasChildren()).isTrue();
        tree.move(a.getId(), root.getId(), null);
        assertThat(closure.ancestors(a1x.getId())).containsExactly(root.getId(), a.getId(), a1.getId(), a1x.getId());
    }

    @Test
    void zoneOfFallsBackToTheDefault() {
        assertThat(regionService.zoneOf(a.getId()).getId()).isEqualTo("Europe/Zurich");
        Region berlin = regions.findById(b.getId()).orElseThrow();
        berlin.setTimeZone("Europe/Berlin");
        regions.flush();
        assertThat(regionService.zoneOf(b.getId()).getId()).isEqualTo("Europe/Berlin");
        assertThat(regionService.zoneOf(RegionFixtures.unknownId()).getId()).isEqualTo("Europe/Zurich");
    }
}
