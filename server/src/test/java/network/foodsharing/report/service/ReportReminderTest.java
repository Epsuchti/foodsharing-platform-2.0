package network.foodsharing.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
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
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.Report;
import network.foodsharing.report.domain.ReportAction;
import network.foodsharing.report.domain.ReportStatus;
import network.foodsharing.report.repository.ReportRepository;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * G1 report reminders (rule the old e2e spec asserted, decision 180): the reminder job turns due reminders of open
 * reports into one REPORT_REMINDER bell to the handlers (else those of the nearest ancestor) with a link to the report,
 * replaces an earlier one, clears the reminder and records it as delivered.
 */
class ReportReminderTest {

    private static final Instant NOW = Instant.parse("2026-11-02T11:30:00Z");

    private final ReportRepository reports = mock(ReportRepository.class);
    private final ReportPermissionService access = mock(ReportPermissionService.class);
    private final RegionService regions = mock(RegionService.class);
    private final UserService users = mock(UserService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final HistoryEvent.Builder event = mock(HistoryEvent.Builder.class, RETURNS_SELF);
    private final ReportService service = new ReportService(reports, null, access, regions, null, users, null,
            notifications, null, history, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID handler = UUID.randomUUID();
    private final Report report = new Report();

    ReportReminderTest() {
        User jonas = TestUsers.user("jumper@local");
        jonas.setDisplayName("Jonas Weber");
        jonas.setId(UUID.randomUUID());
        report.setId(UUID.randomUUID());
        report.setRegionId(UUID.randomUUID());
        report.setReportedUserId(jonas.getId());
        report.setReminderAt(Instant.parse("2026-11-02T11:00:00Z"));
        when(reports.dueReminders(NOW, ReportStatus.COMPLETED)).thenReturn(List.of(report));
        when(users.findActive(jonas.getId())).thenReturn(Optional.of(jonas));
        when(history.event(any(), any())).thenReturn(event);
    }

    @Test
    void aDueReminderRingsTheHandlersOnceAndIsCleared() {
        when(access.handlers(report.getRegionId())).thenReturn(Set.of(handler));

        assertThat(service.sendDueReminders()).isEqualTo(1);

        String identifier = "REPORT_REMINDER-" + report.getId();
        verify(notifications).removeBells(identifier);
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.REPORT_REMINDER);
        assertThat(bell.getValue().recipientIds()).containsExactly(handler);
        assertThat(bell.getValue().link()).isEqualTo("/reports/" + report.getId());
        assertThat(bell.getValue().params()).containsEntry("name", "Jonas Weber");
        assertThat(report.getReminderAt()).isNull();
        verify(reports).save(report);
        verify(history).event(ReportAction.REMINDER_SET, report.getId());
        verify(event).metadata("delivered", true);
    }

    @Test
    void withoutHandlersTheNearestAncestorsTeamIsRung() {
        Region parent = new Region();
        parent.setId(UUID.randomUUID());
        when(access.handlers(report.getRegionId())).thenReturn(Set.of());
        when(regions.ancestors(report.getRegionId())).thenReturn(List.of(parent));
        when(access.handlers(parent.getId())).thenReturn(Set.of(handler));

        assertThat(service.sendDueReminders()).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().recipientIds()).containsExactly(handler);
    }

    @Test
    void nobodyToRingStillClearsTheReminder() {
        when(access.handlers(any())).thenReturn(Set.of());
        when(regions.ancestors(report.getRegionId())).thenReturn(List.of());

        assertThat(service.sendDueReminders()).isZero();
        verify(notifications, never()).notify(any());
        assertThat(report.getReminderAt()).isNull();
    }
}
