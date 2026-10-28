package network.foodsharing.forum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A36 moderators: MODERATION group admins, else ambassadors of the region (nearest) or an ancestor. */
class ForumPermissionServiceTest {

    private final RegionService regions = mock(RegionService.class);
    private final RegionReadService regionRead = mock(RegionReadService.class);
    private ForumPermissionService service;

    private final Region parent = region();
    private final Region city = region();
    private final Region moderationGroup = region();
    private final UUID parentAmbassador = UUID.randomUUID();
    private final UUID cityAmbassador = UUID.randomUUID();
    private final UUID groupAdmin = UUID.randomUUID();

    private static Region region() {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        return region;
    }

    @BeforeEach
    void setUp() {
        service = new ForumPermissionService(mock(PermissionService.class), regions, regionRead,
                mock(RegionOptionsService.class), mock(UserService.class), mock(ForumThreadRepository.class),
                mock(CurrentActorService.class));
        when(regions.ancestors(city.getId())).thenReturn(List.of(parent, city));
        when(regions.adminIds(parent.getId())).thenReturn(Set.of(parentAmbassador));
        when(regions.adminIds(city.getId())).thenReturn(Set.of(cityAmbassador));
        when(regions.adminIds(moderationGroup.getId())).thenReturn(Set.of(groupAdmin));
    }

    @Test
    void withoutModerationGroupTheAmbassadorsModerateAndAncestorsCount() {
        when(regionRead.functionGroups(city.getId())).thenReturn(List.of());
        assertThat(service.moderators(city.getId())).containsExactly(cityAmbassador);
        assertThat(service.isModerator(cityAmbassador, city.getId())).isTrue();
        assertThat(service.isModerator(parentAmbassador, city.getId())).isTrue();
        assertThat(service.isModerator(groupAdmin, city.getId())).isFalse();
    }

    @Test
    void moderationGroupAdminsReplaceTheAmbassadors() {
        when(regionRead.functionGroups(city.getId())).thenReturn(List.of(
                new RegionReadService.FunctionGroup(WorkingGroupFunction.MODERATION, moderationGroup)));
        assertThat(service.moderators(city.getId())).containsExactly(groupAdmin);
        assertThat(service.isModerator(groupAdmin, city.getId())).isTrue();
        assertThat(service.isModerator(cityAmbassador, city.getId())).isFalse();
        assertThat(service.isModerator(parentAmbassador, city.getId())).isFalse();
    }

    @Test
    void aModerationGroupWithoutAdminsFallsBackToTheAmbassadors() {
        when(regions.adminIds(moderationGroup.getId())).thenReturn(Set.of());
        when(regionRead.functionGroups(city.getId())).thenReturn(List.of(
                new RegionReadService.FunctionGroup(WorkingGroupFunction.MODERATION, moderationGroup)));
        assertThat(service.moderators(city.getId())).containsExactly(cityAmbassador);
        assertThat(service.isModerator(cityAmbassador, city.getId())).isTrue();
    }

    @Test
    void theNearestAncestorWithAmbassadorsGetsTheBells() {
        when(regionRead.functionGroups(city.getId())).thenReturn(List.of());
        when(regions.adminIds(city.getId())).thenReturn(Set.of());
        assertThat(service.moderators(city.getId())).containsExactly(parentAmbassador);
    }
}
