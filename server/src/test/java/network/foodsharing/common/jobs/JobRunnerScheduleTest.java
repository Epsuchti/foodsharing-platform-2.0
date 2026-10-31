package network.foodsharing.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import network.foodsharing.common.jobs.repository.JobRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/** {@code app.jobs.scheduled}: only the listed jobs run on their schedule; manual runs always run. */
class JobRunnerScheduleTest {

    private final AtomicInteger runs = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private JobRunner runner(String scheduled) {
        JobRunRepository repository = mock(JobRunRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        JobRunner runner = new JobRunner(repository, mock(PlatformTransactionManager.class), mock(JdbcClient.class),
                Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC), mock(ObjectProvider.class),
                event -> { });
        runner.setScheduledJobs(scheduled);
        return runner;
    }

    private RunnableJob job(String name) {
        return new RunnableJob() {
            @Override
            public String jobName() {
                return name;
            }

            @Override
            public void runNow() {
                runs.incrementAndGet();
            }
        };
    }

    @Test
    void everyJobRunsOnItsScheduleByDefault() {
        JobRunner runner = runner("");
        runner.run(job("pickup-policy"), JobRun.Trigger.SCHEDULE, null);
        runner.run(job("mail-outbox"), JobRun.Trigger.SCHEDULE, null);
        assertThat(runs).hasValue(2);
    }

    @Test
    void onlyListedJobsRunOnTheirScheduleButManualRunsAlwaysRun() {
        JobRunner runner = runner("mail-outbox, search-refresh");
        runner.run(job("mail-outbox"), JobRun.Trigger.SCHEDULE, null);
        assertThat(runs).hasValue(1);
        JobRun skipped = runner.run(job("pickup-policy"), JobRun.Trigger.SCHEDULE, null);
        assertThat(runs).hasValue(1);
        assertThat(skipped.getOutcome()).isEqualTo(JobRun.Outcome.RUNNING);
        assertThat(skipped.getFinishedAt()).isNull();
        runner.run(job("pickup-policy"), JobRun.Trigger.MANUAL, null);
        assertThat(runs).hasValue(2);
    }
}
