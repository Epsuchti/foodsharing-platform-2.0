package network.foodsharing.store.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import network.foodsharing.api.model.StoreDetail;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.user.mapper.UserMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Decision 171 (D4): the store description is for the team ({@code mayReadWall}: members, coordinators, ORGA and
 * the region's store coordinators); the public information goes to everyone who may open the store. Which actor
 * gets {@code mayReadWall} is pinned in {@code StorePermissionMatrixTest}.
 */
class StoreMapperTest {

    private final StoreMapper mapper = new StoreMapper(mock(UserMapper.class), mock(RegionMapper.class));

    record Viewer(String name, boolean wall, UserTeamStatus status) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> viewers() {
        return Stream.of(
                Arguments.of(new Viewer("member", true, UserTeamStatus.MEMBER), true),
                Arguments.of(new Viewer("coordinator", true, UserTeamStatus.COORDINATOR), true),
                Arguments.of(new Viewer("orga, not in team", true, UserTeamStatus.NO_MEMBER), true),
                Arguments.of(new Viewer("region store coordinator, not in team", true, UserTeamStatus.NO_MEMBER), true),
                Arguments.of(new Viewer("waiting list", false, UserTeamStatus.WAITING_LIST), false),
                Arguments.of(new Viewer("region member, not in team", false, UserTeamStatus.NO_MEMBER), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewers")
    void descriptionOnlyForTheTeamPublicInfoForEveryone(Viewer viewer, boolean seesDescription) {
        Store store = new Store();
        store.setName("Bio-Laden");
        store.setDescription("Team notes **in Markdown**");
        store.setPublicInfo("Open for everyone");

        StoreDetail detail = mapper.toDetail(new StoreQueryService.Detail(store, null, null, List.of(), null,
                "Europe/Zurich", List.of(), Map.of(), rights(viewer)));

        assertThat(detail.getDescription()).isEqualTo(seesDescription ? "Team notes **in Markdown**" : null);
        assertThat(detail.getPublicInfo()).isEqualTo("Open for everyone");
    }

    private static StorePermissionService.Rights rights(Viewer v) {
        return new StorePermissionService.Rights(true, v.wall(), v.wall(), false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false, false, false, false, v.status());
    }
}
