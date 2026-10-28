package network.foodsharing.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.map.domain.StoreMarkerHelp;
import network.foodsharing.map.domain.StoreMarkerStatus;
import network.foodsharing.map.domain.UserMarkerMember;
import network.foodsharing.map.domain.UserMarkerRole;
import network.foodsharing.map.service.MarkerService;
import network.foodsharing.map.service.MarkerService.StoreMarker;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** M1 marker filters and rounding. */
class MarkerServiceTest {

    private static StoreMarker store(CooperationStatus status, TeamSearchStatus team) {
        return new StoreMarker(UUID.randomUUID(), 47.0, 8.0, "S", status, team, null, UUID.randomUUID());
    }

    @Test
    void storeStatusAndHelpFilters() {
        StoreMarker cooperating = store(CooperationStatus.COOPERATION_ESTABLISHED, TeamSearchStatus.OPEN_SEARCHING);
        StoreMarker negotiating = store(CooperationStatus.IN_NEGOTIATION, TeamSearchStatus.CLOSED);
        assertThat(MarkerService.matches(cooperating, StoreMarkerStatus.COOPERATING, null)).isTrue();
        assertThat(MarkerService.matches(negotiating, StoreMarkerStatus.COOPERATING, null)).isFalse();
        assertThat(MarkerService.matches(negotiating, StoreMarkerStatus.NOT_COOPERATING, null)).isTrue();
        assertThat(MarkerService.matches(cooperating, StoreMarkerStatus.ALL, StoreMarkerHelp.SEARCHING)).isTrue();
        assertThat(MarkerService.matches(negotiating, StoreMarkerStatus.ALL, StoreMarkerHelp.OPEN)).isFalse();
        StoreMarker open = store(CooperationStatus.COOPERATION_ESTABLISHED, TeamSearchStatus.OPEN);
        assertThat(MarkerService.matches(open, null, StoreMarkerHelp.OPEN)).isTrue();
        assertThat(MarkerService.matches(open, null, StoreMarkerHelp.SEARCHING)).isFalse();
    }

    @Test
    void privatePositionsAreRoundedToAbout100Metres() {
        assertThat(MarkerService.round(47.38912)).isEqualTo(47.389);
        assertThat(MarkerService.round(8.54871)).isEqualTo(8.549);
    }

    @Test
    void userFilters() {
        UUID region = UUID.randomUUID();
        User user = new User();
        user.setRole(Role.STORE_MANAGER);
        user.setHomeRegionId(region);
        user.setLastLoginAt(Instant.parse("2026-10-20T00:00:00Z"));
        Instant weekAgo = Instant.parse("2026-10-21T00:00:00Z");
        assertThat(MarkerService.matches(user, region, UserMarkerRole.STORE_MANAGER, null,
                UserMarkerMember.HOMEREGION)).isTrue();
        assertThat(MarkerService.matches(user, region, UserMarkerRole.FOODSAVER, null, null)).isFalse();
        assertThat(MarkerService.matches(user, region, null, weekAgo, null)).isFalse();
        assertThat(MarkerService.matches(user, region, null, null, UserMarkerMember.OTHER)).isFalse();
        assertThat(MarkerService.matches(user, UUID.randomUUID(), UserMarkerRole.ALL, null, UserMarkerMember.OTHER))
                .isTrue();
    }
}
