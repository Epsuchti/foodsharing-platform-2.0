package network.foodsharing.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.ReportHandlerRule;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import org.junit.jupiter.api.Test;

/** {@code RegionDetail.mayHandleReports} follows the A51 handler rule of {@link ReportPermissionService}. */
class ReportHandlerRuleTest {

    private final UUID root = UUID.randomUUID();
    private final UUID region = UUID.randomUUID();
    private final UUID reportGroup = UUID.randomUUID();
    private final UUID arbitrationGroup = UUID.randomUUID();
    private final UUID rootAmbassador = UUID.randomUUID();
    private final UUID ambassador = UUID.randomUUID();
    private final UUID reportAdmin = UUID.randomUUID();
    private final UUID arbitrationAdmin = UUID.randomUUID();
    private final RegionService regions = mock(RegionService.class);
    private final RegionReadService regionRead = mock(RegionReadService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ReportHandlerRule rule = new ReportPermissionService(permissions, regions, regionRead, null, null);

    @Test
    void reportGroupAdminsReplaceTheAmbassadorsAndArbitrationAdminsAndAncestorHandlersJoin() {
        tree(true);

        assertThat(rule.mayHandleReports(user(reportAdmin), region)).isTrue();
        assertThat(rule.mayHandleReports(user(arbitrationAdmin), region)).isTrue();
        assertThat(rule.mayHandleReports(user(rootAmbassador), region)).isTrue();
        assertThat(rule.mayHandleReports(user(ambassador), region)).isFalse();
        assertThat(rule.mayHandleReports(user(UUID.randomUUID()), region)).isFalse();
        assertThat(rule.mayHandleReports(user(reportAdmin), root)).isFalse();
    }

    @Test
    void withoutAReportGroupTheAmbassadorsHandleAndOrgaAlways() {
        tree(false);

        assertThat(rule.mayHandleReports(user(ambassador), region)).isTrue();
        assertThat(rule.mayHandleReports(user(reportAdmin), region)).isFalse();
        SessionPrincipal orga = user(UUID.randomUUID());
        when(permissions.isOrga(orga)).thenReturn(true);
        assertThat(rule.mayHandleReports(orga, region)).isTrue();
        assertThat(rule.mayHandleReports(null, region)).isFalse();
    }

    private void tree(boolean withReportGroup) {
        when(regions.ancestors(region)).thenReturn(List.of(region(root), region(region)));
        when(regions.ancestors(root)).thenReturn(List.of(region(root)));
        when(regions.adminIds(root)).thenReturn(Set.of(rootAmbassador));
        when(regions.adminIds(region)).thenReturn(Set.of(ambassador));
        when(regions.adminIds(reportGroup)).thenReturn(Set.of(reportAdmin));
        when(regions.adminIds(arbitrationGroup)).thenReturn(Set.of(arbitrationAdmin));
        when(regionRead.functionGroups(any())).thenReturn(List.of());
        List<RegionReadService.FunctionGroup> groups = withReportGroup
                ? List.of(new RegionReadService.FunctionGroup(WorkingGroupFunction.REPORT, region(reportGroup)),
                        new RegionReadService.FunctionGroup(WorkingGroupFunction.ARBITRATION, region(arbitrationGroup)))
                : List.of();
        when(regionRead.functionGroups(region)).thenReturn(groups);
    }

    private static Region region(UUID id) {
        Region region = new Region();
        region.setId(id);
        return region;
    }

    private static SessionPrincipal user(UUID id) {
        return new SessionPrincipal(id, UUID.randomUUID(), Role.FOODSAVER, "user@local");
    }
}
