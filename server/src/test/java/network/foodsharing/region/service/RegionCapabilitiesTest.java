package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.Test;

/** Decision 161: Neulinge, Verifizierung, Mitglieder in the menu only for those who manage users. */
class RegionCapabilitiesTest {

    private static final UUID HOME = UUID.randomUUID();

    private final PermissionService permissions = mock(PermissionService.class);
    private final RegionCapabilities enricher = new RegionCapabilities(permissions);

    private static SessionPrincipal principal(Role role) {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "x@test.local");
    }

    private SessionInfoBuilder enrich(SessionPrincipal principal, String... granted) {
        // the region enricher has added an admin unit (adminOfAnyUnit), so the administration query runs
        SessionInfoBuilder builder = new SessionInfoBuilder().addUnit(new SessionInfoBuilder.Unit(UUID.randomUUID(), "unit", "unit", "CITY", null, true, true, false, null))
                .homeRegionId(HOME).grantedPermissions(Set.of(granted));
        enricher.enrich(principal, builder);
        return builder;
    }

    @Test
    void ambassadorOfTheHomeRegionManagesNewcomersVerificationAndMembers() {
        SessionPrincipal ambassador = principal(Role.AMBASSADOR);
        when(permissions.mayAdministrateRegion(ambassador, HOME)).thenReturn(true);

        assertThat(enrich(ambassador).capabilities()).containsExactly("MANAGE_NEWCOMERS", "VERIFY", "MANAGE_MEMBERS");
    }

    @Test
    void holderOfCanManageUsersSomewhereGetsThemToo() {
        SessionPrincipal manager = principal(Role.FOODSAVER);

        assertThat(enrich(manager, "CAN_MANAGE_USERS@" + UUID.randomUUID()).capabilities())
                .contains("MANAGE_NEWCOMERS", "VERIFY", "MANAGE_MEMBERS");
        assertThat(enrich(manager, "CAN_GIVE_INTRO_PICKUPS@" + UUID.randomUUID()).capabilities()).isEmpty();
    }

    @Test
    void usersWhoAdministrateNoUnitSkipTheQuery() {
        SessionPrincipal foodsaver = principal(Role.FOODSAVER);
        SessionInfoBuilder builder = new SessionInfoBuilder().homeRegionId(HOME);
        enricher.enrich(foodsaver, builder);

        assertThat(builder.capabilities()).isEmpty();
        org.mockito.Mockito.verify(permissions, org.mockito.Mockito.never()).mayAdministrateRegion(foodsaver, HOME);
    }

    @Test
    void orgaGetsThem() {
        SessionPrincipal orga = principal(Role.ORGA);
        when(permissions.isOrga(orga)).thenReturn(true);

        assertThat(enrich(orga).capabilities()).hasSize(3);
    }

    @Test
    void workingGroupAdminWithoutUserRightsGetsNone() {
        // Nina: admin of the working group Betriebskoordination, no region administration, no CAN_MANAGE_USERS
        SessionPrincipal nina = principal(Role.STORE_MANAGER);
        when(permissions.mayAdministrateRegion(nina, HOME)).thenReturn(false);

        assertThat(enrich(nina, "CAN_USE_AUTOMATIONS@" + UUID.randomUUID()).capabilities()).isEmpty();
    }
}
