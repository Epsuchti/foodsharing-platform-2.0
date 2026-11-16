package network.foodsharing.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.achievement.service.AchievementPermissionService;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.Test;

/** AC1: who administers global and regional achievements, who counts as member and as recipient. */
class AchievementPermissionServiceTest {

    static final UUID REGION = UUID.randomUUID();

    PermissionService permissions = mock(PermissionService.class);
    MembershipService memberships = mock(MembershipService.class);
    AchievementPermissionService access = new AchievementPermissionService(permissions, memberships);

    SessionPrincipal ambassador = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "a@x");

    @Test
    void globalAchievementsAreAdministeredByOrgaOnly() {
        when(permissions.mayAdministrateRegion(ambassador, REGION)).thenReturn(true);
        assertThat(access.mayAdminister(ambassador, (UUID) null)).isFalse();
        assertThatThrownBy(() -> access.requireAdminister(ambassador, null)).isInstanceOf(ApiException.class);
        when(permissions.isOrga(ambassador)).thenReturn(true);
        assertThat(access.mayAdminister(ambassador, (UUID) null)).isTrue();
    }

    @Test
    void regionalAchievementsAreAdministeredByRegionAdministrators() {
        assertThat(access.mayAdminister(ambassador, REGION)).isFalse();
        when(permissions.mayAdministrateRegion(ambassador, REGION)).thenReturn(true);
        assertThat(access.mayAdminister(ambassador, REGION)).isTrue();
    }

    @Test
    void everybodyIsMemberOfGlobalAchievementsOnlyRegionMembersOfRegionalOnes() {
        assertThat(access.isMember(ambassador, null)).isTrue();
        assertThat(access.isMember(ambassador, REGION)).isFalse();
        when(permissions.isMemberOf(ambassador, REGION)).thenReturn(true);
        assertThat(access.isMember(ambassador, REGION)).isTrue();
    }

    @Test
    void recipientMustBeActiveMemberOfTheRegionNotJustAdmin() {
        UUID user = UUID.randomUUID();
        Region region = new Region();
        region.setId(REGION);
        when(memberships.units(user)).thenReturn(List.of(new MembershipService.Unit(region, false, true, false, null)));
        assertThat(access.isActiveMember(user, REGION)).isFalse();
        assertThat(access.isActiveMember(user, null)).isTrue();
        when(memberships.units(user)).thenReturn(List.of(new MembershipService.Unit(region, true, false, true, null)));
        assertThat(access.isActiveMember(user, REGION)).isTrue();
    }

    @Test
    void viewerCarriesStoreManagerRoleAndRecipient() {
        SessionPrincipal manager = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.STORE_MANAGER, "m@x");
        when(permissions.hasRole(manager, Role.STORE_MANAGER)).thenReturn(true);
        var viewer = access.viewer(manager, REGION, manager.userId());
        assertThat(viewer.storeManager()).isTrue();
        assertThat(viewer.recipient()).isTrue();
        assertThat(access.viewer(manager, REGION, UUID.randomUUID()).recipient()).isFalse();
    }
}
