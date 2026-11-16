package network.foodsharing.admin.service;

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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.common.jobs.repository.JobRunRepository;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.IntegrationTestMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * C15 / D6.3 (A25): the ORGA's "run now" runs a job by name with the MANUAL trigger and the ORGA as actor, records an OK
 * run and a JOB_RUN_MANUAL history entry; an unknown job is 404 JOB_UNKNOWN for "run now" and for its runs. Real
 * {@link JobRunner} over mocked persistence, no Spring context.
 */
class AdminOperationsServiceTest {

    private static final UUID ORGA = UUID.fromString("00000001-0001-4000-8000-000000000001");

    private final AtomicInteger cleanups = new AtomicInteger();
    private final JobRunRepository runs = mock(JobRunRepository.class);
    private final HistoryService history = mock(HistoryService.class);
    private AdminOperationsService operations;

    private final RunnableJob cleanup = new RunnableJob() {
        @Override
        public String jobName() {
            return "cleanup";
        }

        @Override
        public void runNow() {
            cleanups.incrementAndGet();
        }
    };

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        ObjectProvider<RunnableJob> jobs = mock(ObjectProvider.class);
        when(jobs.stream()).thenAnswer(invocation -> Stream.of(cleanup));
        JobRunner runner = new JobRunner(runs, mock(PlatformTransactionManager.class), mock(JdbcClient.class),
                Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC), jobs, event -> { });
        operations = new AdminOperationsService(runner, mock(IntegrationRegistry.class), history,
                mock(IntegrationTestMessages.class));
    }

    @Test
    void runNowRunsTheJobManuallyAndRecordsIt() {
        JobRun run = operations.runNow("cleanup", ORGA);

        assertThat(cleanups).hasValue(1);
        assertThat(run.getJobName()).isEqualTo("cleanup");
        assertThat(run.getOutcome()).isEqualTo(JobRun.Outcome.OK);
        assertThat(run.getTrigger()).isEqualTo(JobRun.Trigger.MANUAL);
        assertThat(run.getActorId()).isEqualTo(ORGA);
        ArgumentCaptor<HistoryEvent> event = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(history).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AdminAction.JOB_RUN_MANUAL);
    }

    @Test
    void unknownJobsAre404() {
        assertThatThrownBy(() -> operations.runNow("no-such-job", ORGA))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.JOB_UNKNOWN));
        assertThatThrownBy(() -> operations.runs("no-such-job", 0, 20))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.JOB_UNKNOWN));
        assertThat(cleanups).hasValue(0);
        verify(history, never()).record(any());
    }
}
