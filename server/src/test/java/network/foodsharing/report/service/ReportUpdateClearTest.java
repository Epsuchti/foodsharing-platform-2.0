package network.foodsharing.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.report.domain.Report;
import network.foodsharing.report.domain.ReportAction;
import network.foodsharing.report.repository.ReportRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.Test;

/** A51 report update: consequence, reminder and forum thread can be cleared; each clear is a history entry. */
class ReportUpdateClearTest {

    private static final Instant REMINDER = Instant.parse("2026-11-10T08:00:00Z");
    private static final UUID THREAD = UUID.randomUUID();

    private final Report report = new Report();
    private final HistoryService history = mock(HistoryService.class);
    private final HistoryEvent.Builder event = mock(HistoryEvent.Builder.class, RETURNS_SELF);
    private final ReportService service = service();

    @Test
    void clearFlagsAndAnEmptyConsequenceRemoveTheValuesAndRecordIt() {
        service.update(report.getId(), new ReportService.ReportChange(null, "", null, true, null, true));

        assertThat(report.getConsequence()).isNull();
        assertThat(report.getReminderAt()).isNull();
        assertThat(report.getForumThreadId()).isNull();
        verify(history).event(ReportAction.CONSEQUENCE_SET, report.getId());
        verify(event).change("consequence", "Verwarnung", null);
        verify(history).event(ReportAction.REMINDER_SET, report.getId());
        verify(event).change("reminderAt", REMINDER, null);
        verify(history).event(ReportAction.THREAD_LINKED, report.getId());
        verify(event).change("forumThreadId", THREAD, null);
    }

    @Test
    void omittedFieldsStayAndClearingNothingRecordsNothing() {
        service.update(report.getId(), new ReportService.ReportChange(null, null, null, false, null, false));
        assertThat(report.getReminderAt()).isEqualTo(REMINDER);
        assertThat(report.getForumThreadId()).isEqualTo(THREAD);
        verify(history, never()).event(any(), any());

        report.setReminderAt(null);
        service.update(report.getId(), new ReportService.ReportChange(null, null, null, true, null, false));
        verify(history, never()).event(any(), any());
    }

    @Test
    void clearTogetherWithAValueIsRejected() {
        assertThatThrownBy(() -> service.update(report.getId(),
                new ReportService.ReportChange(null, null, REMINDER, true, null, false)))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).getArgs().get("field"))
                .isEqualTo("clearReminder");
        assertThatThrownBy(() -> service.update(report.getId(),
                new ReportService.ReportChange(null, null, null, false, THREAD, true)))
                .isInstanceOf(ApiException.class).extracting(ex -> ((ApiException) ex).getArgs().get("field"))
                .isEqualTo("clearForumThread");
        assertThat(report.getReminderAt()).isEqualTo(REMINDER);
        assertThat(report.getForumThreadId()).isEqualTo(THREAD);
    }

    private ReportService service() {
        report.setId(UUID.randomUUID());
        report.setRegionId(UUID.randomUUID());
        report.setConsequence("Verwarnung");
        report.setReminderAt(REMINDER);
        report.setForumThreadId(THREAD);
        ReportRepository reports = mock(ReportRepository.class);
        when(reports.findByIdAndDeletedAtIsNull(report.getId())).thenReturn(Optional.of(report));
        when(reports.save(report)).thenReturn(report);
        CurrentActorService actors = mock(CurrentActorService.class);
        when(actors.requirePrincipal()).thenReturn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                Role.AMBASSADOR, "handler@local"));
        when(history.event(any(), any())).thenReturn(event);
        return new ReportService(reports, null, mock(ReportPermissionService.class), null, null, null, actors, null,
                null, history, Clock.fixed(REMINDER, java.time.ZoneOffset.UTC));
    }
}
