package network.foodsharing.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.Report;
import network.foodsharing.report.repository.ReportRepository;
import network.foodsharing.report.repository.ReportWallPostRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A6/G1 privacy: the violation count on a profile is shown only to people who may see the user's reports (the report
 * team of one of the user's regions, ORGA) and counts only the reports of regions they handle; others get none.
 */
class ReportViolationCountTest {

    private final ReportRepository reports = mock(ReportRepository.class);
    private final ReportPermissionService access = mock(ReportPermissionService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final ReportService service = new ReportService(reports, mock(ReportWallPostRepository.class), access,
            mock(RegionService.class), mock(StoreQueryService.class), mock(UserService.class), actors,
            mock(NotificationService.class), mock(MailOutboxService.class), mock(HistoryService.class, RETURNS_DEEP_STUBS),
            Clock.systemUTC());

    private final SessionPrincipal viewer = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR,
            "reports@test.local");
    private final UUID reported = UUID.randomUUID();
    private final UUID zurich = UUID.randomUUID();
    private final UUID berlin = UUID.randomUUID();

    private static Report report(UUID region) {
        Report report = new Report();
        report.setRegionId(region);
        return report;
    }

    @BeforeEach
    void setUp() {
        when(actors.requirePrincipal()).thenReturn(viewer);
        when(reports.findByReportedUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(reported))
                .thenReturn(List.of(report(zurich), report(zurich), report(berlin)));
    }

    @Test
    void peopleWhoMayNotSeeTheReportsGetNoCount() {
        when(access.mayAccessReportsForUser(viewer, reported)).thenReturn(false);
        assertThat(service.visibleReportCount(reported)).isEmpty();
    }

    @Test
    void theReportTeamCountsOnlyTheReportsOfRegionsItHandles() {
        when(access.mayAccessReportsForUser(viewer, reported)).thenReturn(true);
        when(access.mayHandle(eq(viewer), any())).thenAnswer(call -> zurich.equals(call.getArgument(1)));
        assertThat(service.visibleReportCount(reported)).hasValue(2);
    }
}
