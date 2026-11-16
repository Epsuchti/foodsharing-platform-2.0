package network.foodsharing.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.Report;
import network.foodsharing.report.domain.ReportReason;
import network.foodsharing.report.repository.ReportRepository;
import network.foodsharing.report.repository.ReportWallPostRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * G1/G2 service paths without a database (formerly ReportFlowIT): a filed report rings the region's handlers (never
 * the reporter or the reported member) and mails the reporter a confirmation on request; a reason outside the
 * region's set is refused before anything is written; reports about a person are only for handlers of one of that
 * person's regions (and only the reports of regions they handle); a deleted report takes its bells along.
 */
class ReportServiceFlowTest {

    static final UUID REGION = UUID.randomUUID();
    static final UUID OTHER_REGION = UUID.randomUUID();
    static final SessionPrincipal REPORTER = TestAuth.principal(SeedUsers.FOODSAVER);
    static final UUID HANDLER = UUID.randomUUID();

    ReportRepository reports = mock(ReportRepository.class);
    ReportPermissionService access = mock(ReportPermissionService.class);
    UserService users = mock(UserService.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    NotificationService notifications = mock(NotificationService.class);
    MailOutboxService mails = mock(MailOutboxService.class);
    HistoryService history = mock(HistoryService.class);
    ReportService service = new ReportService(reports, mock(ReportWallPostRepository.class), access,
            mock(RegionService.class), mock(StoreQueryService.class), users, actors, notifications, mails, history,
            Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));
    User reporter = user(REPORTER.userId(), "foodsaver@local");
    User reported = user(UUID.randomUUID(), "jumper@local");

    @BeforeEach
    void setUp() {
        reported.setHomeRegionId(REGION);
        when(actors.requirePrincipal()).thenReturn(REPORTER);
        when(users.requireActive(REPORTER.userId())).thenReturn(reporter);
        when(users.findActive(reported.getId())).thenReturn(Optional.of(reported));
        when(users.require(reported.getId())).thenReturn(reported);
        when(access.reporterFacts(reporter, reported)).thenReturn(new ReportRules.ReporterFacts(false, true, true));
        when(access.regionOptions(REGION)).thenReturn(
                new RegionOptionsService.Options(true, true, null, true, 1, false, false));
        when(access.handlers(REGION)).thenReturn(Set.of(HANDLER, REPORTER.userId(), reported.getId()));
        when(reports.save(any())).thenAnswer(call -> {
            Report report = call.getArgument(0);
            if (report.getId() == null) {
                report.setId(UUID.randomUUID());
            }
            return report;
        });
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
    }

    static User user(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(email);
        return user;
    }

    @Test
    void aReportRingsTheHandlersOnlyAndConfirmsByMail() {
        Report report = service.create(reported.getId(), new ReportService.ReportInput(ReportReason.LATE, null,
                "Wieder eine Stunde zu spät gekommen.", null, true));

        assertThat(report.getRegionId()).isEqualTo(REGION);
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.REPORT_NEW);
        assertThat(bell.getValue().recipientIds()).containsExactly(HANDLER);
        ArgumentCaptor<MailDraft> mail = ArgumentCaptor.forClass(MailDraft.class);
        verify(mails).enqueue(mail.capture());
        assertThat(mail.getValue().to()).isEqualTo("foodsaver@local");
    }

    @Test
    void aReasonOutsideTheRegionsSetIsRefusedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> service.create(reported.getId(), new ReportService.ReportInput(
                ReportReason.VIOLATED_B13, null, "Regel B13 missachtet.", null, false)))
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.REPORT_REASON_NOT_ALLOWED));
        assertThatThrownBy(() -> service.create(reported.getId(), new ReportService.ReportInput(
                ReportReason.OTHER, "Ton", "Unfreundlich bei der Übergabe.", null, false)))
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.REPORT_REASON_NOT_ALLOWED));
        verify(reports, never()).save(any());
        verify(notifications, never()).notify(any());
    }

    @Test
    void reportsAboutAPersonAreForTheirHandlersOnly() {
        when(access.mayAccessReportsForUser(REPORTER, reported.getId())).thenReturn(false);
        assertThatThrownBy(() -> service.listForUser(reported.getId()))
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.REPORTS_NOT_ACCESSIBLE));

        Report handled = new Report();
        handled.setRegionId(REGION);
        Report elsewhere = new Report();
        elsewhere.setRegionId(OTHER_REGION);
        when(access.mayAccessReportsForUser(REPORTER, reported.getId())).thenReturn(true);
        when(access.mayHandle(REPORTER, REGION)).thenReturn(true);
        when(access.mayHandle(REPORTER, OTHER_REGION)).thenReturn(false);
        when(reports.findByReportedUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(reported.getId()))
                .thenReturn(List.of(handled, elsewhere));
        assertThat(service.listForUser(reported.getId())).containsExactly(handled);
    }

    @Test
    void onlyOrgaDeletesAndTheBellsGoAlong() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setRegionId(REGION);
        report.setStatus(network.foodsharing.report.domain.ReportStatus.TO_DO);
        when(reports.findByIdAndDeletedAtIsNull(report.getId())).thenReturn(Optional.of(report));
        when(access.isOrga(REPORTER)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(report.getId())).isInstanceOf(ApiException.class);
        verify(notifications, never()).removeBells(any());

        when(access.isOrga(REPORTER)).thenReturn(true);
        service.delete(report.getId());
        verify(notifications).removeBells("REPORT_NEW-" + report.getId());
        verify(notifications).removeBells("REPORT_REMINDER-" + report.getId());
    }
}
