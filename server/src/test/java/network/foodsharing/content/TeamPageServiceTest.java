package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.content.service.TeamPageService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;

/** C16: flagged groups with their active members, sorted by name, with avatar and position description. */
class TeamPageServiceTest {

    @Test
    void groupsWithSortedMembers() {
        RegionService regions = mock(RegionService.class);
        UserService users = mock(UserService.class);
        Region it = new Region();
        it.setId(UUID.randomUUID());
        it.setName("IT");
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(regions.teamPageGroups()).thenReturn(List.of(it));
        when(regions.activeMemberIds(it.getId())).thenReturn(ids);
        when(users.findActive(ids)).thenReturn(List.of(user("zora", null), user("Anna", "Server")));

        List<TeamPageService.Group> groups = new TeamPageService(regions, users).groups();

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().members()).extracting(TeamPageService.Member::displayName)
                .containsExactly("Anna", "zora");
        assertThat(groups.getFirst().members().getFirst().positionDescription()).isEqualTo("Server");
        assertThat(groups.getFirst().members().getFirst().avatarUrl()).isEqualTo("/a/Anna");
    }

    private static User user(String name, String position) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        user.setAvatarUrl("/a/" + name);
        user.setPositionDescription(position);
        return user;
    }
}
