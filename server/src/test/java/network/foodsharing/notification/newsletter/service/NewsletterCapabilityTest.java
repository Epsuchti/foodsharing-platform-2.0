package network.foodsharing.notification.newsletter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.Test;

/** Decision 159: Newsletter in the menu for those who may create one (ORGA, region administrators). */
class NewsletterCapabilityTest {

    private final PermissionService permissions = mock(PermissionService.class);

    private boolean newsletters(SessionPrincipal principal) {
        SessionInfoBuilder builder = new SessionInfoBuilder().addUnit(new SessionInfoBuilder.Unit(UUID.randomUUID(), "unit", "unit", "CITY", null, true, true, false, null));
        new NewsletterIntegrations().newsletterSessionInfoEnricher(permissions).enrich(principal, builder);
        return builder.capabilities().contains("NEWSLETTERS");
    }

    @Test
    void regionAdministratorsSeeNewsletters() {
        SessionPrincipal ambassador = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "a@x");
        when(permissions.mayAdministrateAnyRegion(ambassador)).thenReturn(true);
        assertThat(newsletters(ambassador)).isTrue();
    }

    @Test
    void workingGroupAdminsDoNot() {
        SessionPrincipal nina = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.STORE_MANAGER, "n@x");
        when(permissions.mayAdministrateAnyRegion(nina)).thenReturn(false);
        assertThat(newsletters(nina)).isFalse();
    }

    @Test
    void permissionServiceAsksTheResolverOnlyForNonOrga() {
        PermissionService real = new PermissionService();
        assertThat(real.mayAdministrateAnyRegion(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.ORGA,
                "o@x"))).isTrue();
        assertThat(real.mayAdministrateAnyRegion(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                Role.AMBASSADOR, "a@x"))).isFalse();
        assertThat(real.mayAdministrateAnyRegion(null)).isFalse();
    }
}
