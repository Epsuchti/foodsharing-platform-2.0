package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.security.Permission;
import network.foodsharing.security.WorkingGroupFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The resolver's rules in isolation (the full matrix on real tables is {@link PermissionServiceMatrixTest}). */
class RegionPermissionResolverTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID REGION = UUID.randomUUID();
    private static final UUID GROUP = UUID.randomUUID();

    private final RegionMembershipRepository memberships = mock(RegionMembershipRepository.class);
    private final RegionAdminRepository admins = mock(RegionAdminRepository.class);
    private final RegionFunctionRepository functions = mock(RegionFunctionRepository.class);
    private final RegionPermissionRepository permissions = mock(RegionPermissionRepository.class);
    private final AppProperties properties = new AppProperties();
    private RegionPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RegionPermissionResolver(memberships, admins, functions, permissions,
                mock(RegionRepository.class), mock(RegionClosureRepository.class), properties);
        when(memberships.findActiveRegionIds(USER)).thenReturn(Set.of(GROUP));
        RegionFunction assignment = new RegionFunction();
        assignment.setRegionId(REGION);
        assignment.setGroupId(GROUP);
        assignment.setFunction(WorkingGroupFunction.WELCOME);
        when(functions.findByGroupIdIn(any())).thenReturn(List.of(assignment));
        when(functions.findByRegionIdAndFunction(REGION, WorkingGroupFunction.WELCOME)).thenReturn(Optional.of(assignment));
    }

    @Test
    void defaultFunctionGrantsFollowA10() {
        assertThat(AppProperties.Permissions.defaultFunctionGrants()).containsExactlyInAnyOrderEntriesOf(Map.of(
                WorkingGroupFunction.FSMANAGEMENT, List.of(Permission.CAN_MANAGE_USERS),
                WorkingGroupFunction.WELCOME, List.of(Permission.CAN_GIVE_INTRO_PICKUPS),
                WorkingGroupFunction.STORES_COORDINATION, List.of(Permission.CAN_USE_AUTOMATIONS,
                        Permission.CAN_SEE_USER_PICKUP_COUNT_GROUPING, Permission.CAN_SEE_ALL_AUTOMATION_DECISIONS),
                WorkingGroupFunction.VOTING, List.of(Permission.CAN_MANAGE_SURVEYS)));
        assertThat(WorkingGroupFunction.REPORT.isRestricted()).isTrue();
        assertThat(WorkingGroupFunction.WELCOME.isRestricted()).isFalse();
    }

    @Test
    void membersOfAFunctionGroupGetItsPermissionsInTheRegion() {
        assertThat(resolver.hasRegionPermission(USER, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).isTrue();
        assertThat(resolver.hasRegionPermission(USER, Permission.CAN_MANAGE_USERS, REGION)).isFalse();
        assertThat(resolver.permissionsByRegion(USER)).isEqualTo(Map.of(REGION, Set.of(Permission.CAN_GIVE_INTRO_PICKUPS)));
    }

    @Test
    void grantsAreConfigurable() {
        properties.getPermissions().setFunctionGrants(Map.of(WorkingGroupFunction.WELCOME,
                List.of(Permission.CAN_MANAGE_USERS)));
        assertThat(resolver.hasRegionPermission(USER, Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).isFalse();
        assertThat(resolver.hasRegionPermission(USER, Permission.CAN_MANAGE_USERS, REGION)).isTrue();
    }

    @Test
    void directGrantsCountWithoutGroups() {
        UUID other = UUID.randomUUID();
        when(permissions.existsByUserIdAndRegionIdAndPermission(USER, other, Permission.CAN_MANAGE_SURVEYS))
                .thenReturn(true);
        assertThat(resolver.hasRegionPermission(USER, Permission.CAN_MANAGE_SURVEYS, other)).isTrue();
    }

    @Test
    void functionRightsGoToGroupAdminsOrElseAmbassadors() {
        when(admins.existsByUserIdAndRegionId(USER, GROUP)).thenReturn(true);
        assertThat(resolver.hasFunctionGroupPermission(USER, WorkingGroupFunction.WELCOME, REGION)).isTrue();
        // no PR group: the ambassadors of the region decide
        when(functions.findByRegionIdAndFunction(REGION, WorkingGroupFunction.PR)).thenReturn(Optional.empty());
        assertThat(resolver.hasFunctionGroupPermission(USER, WorkingGroupFunction.PR, REGION)).isFalse();
        when(admins.existsByUserIdAndRegionId(USER, REGION)).thenReturn(true);
        assertThat(resolver.hasFunctionGroupPermission(USER, WorkingGroupFunction.PR, REGION)).isTrue();
    }

    @Test
    void nobodyAdministratesWithoutAdminRoles() {
        when(admins.findRegionIds(USER)).thenReturn(Set.of());
        assertThat(resolver.mayAdministrateRegion(USER, REGION)).isFalse();
        assertThat(resolver.mayAdministrateUser(USER, UUID.randomUUID())).isFalse();
    }
}
