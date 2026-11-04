package network.foodsharing.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.Role;
import network.foodsharing.content.repository.BlogPostRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.Test;

/** Decision 161: Blog verwalten for editors and region ambassadors, Inhalte for ORGA and PR group admins. */
class ContentCapabilitiesTest {

    private final RegionService regions = mock(RegionService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final BlogService blogs = new BlogService(mock(BlogPostRepository.class), regions,
            mock(CurrentActorService.class), permissions, mock(NotificationService.class), mock(UploadService.class),
            mock(FeatureToggleService.class), mock(HistoryService.class), Clock.systemUTC());

    private static SessionPrincipal principal(Role role) {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "x@test.local");
    }

    private static Region unit(UnitType type) {
        Region region = new Region();
        region.setId(UUID.randomUUID());
        region.setType(type);
        return region;
    }

    private List<String> capabilities(SessionPrincipal principal) {
        SessionInfoBuilder builder = new SessionInfoBuilder().addUnit(new SessionInfoBuilder.Unit(UUID.randomUUID(), "unit", "unit", "CITY", null, true, true, false, null));
        new ContentCapabilities().contentSessionInfoEnricher(blogs, permissions).enrich(principal, builder);
        return builder.capabilities();
    }

    private void adminOf(SessionPrincipal principal, Region unit) {
        when(regions.adminRegionIds(principal.userId())).thenReturn(Set.of(unit.getId()));
        when(regions.findAll(Set.of(unit.getId()))).thenReturn(List.of(unit));
    }

    @Test
    void orgaAndPrAdminsManageBlogAndContent() {
        SessionPrincipal orga = principal(Role.ORGA);
        when(permissions.isOrga(orga)).thenReturn(true);
        SessionPrincipal pr = principal(Role.FOODSAVER);
        adminOf(pr, unit(UnitType.WORKING_GROUP));
        when(regions.isFunctionGroupAdminAnywhere(pr.userId(), WorkingGroupFunction.PR)).thenReturn(true);

        assertThat(capabilities(orga)).containsExactly("BLOG_MANAGE", "CONTENT_EDIT");
        assertThat(capabilities(pr)).containsExactly("BLOG_MANAGE", "CONTENT_EDIT");
    }

    @Test
    void ambassadorOfARegionManagesTheBlogOnly() {
        SessionPrincipal ambassador = principal(Role.AMBASSADOR);
        adminOf(ambassador, unit(UnitType.CITY));

        assertThat(capabilities(ambassador)).containsExactly("BLOG_MANAGE");
    }

    @Test
    void usersWhoAdministrateNothingCostOneQuery() {
        SessionPrincipal foodsaver = principal(Role.FOODSAVER);
        when(regions.adminRegionIds(foodsaver.userId())).thenReturn(Set.of());

        assertThat(capabilities(foodsaver)).isEmpty();
        org.mockito.Mockito.verify(regions, org.mockito.Mockito.never()).isFunctionGroupAdminAnywhere(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void workingGroupAdminGetsNeither() {
        SessionPrincipal nina = principal(Role.STORE_MANAGER);
        adminOf(nina, unit(UnitType.WORKING_GROUP));

        assertThat(capabilities(nina)).isEmpty();
    }
}
