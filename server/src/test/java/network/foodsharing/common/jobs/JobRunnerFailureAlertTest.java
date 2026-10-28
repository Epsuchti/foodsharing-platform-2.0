package network.foodsharing.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import network.foodsharing.common.jobs.repository.JobRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/** A25: AUTOMATION_RUN_FAILED is triggered once when a job fails twice in a row; a success resets the streak. */
class JobRunnerFailureAlertTest {

    private final List<Object> events = new ArrayList<>();
    private boolean failing;

    @SuppressWarnings("unchecked")
    private JobRunner runner() {
        JobRunRepository runs = mock(JobRunRepository.class);
        when(runs.save(any())).thenAnswer(i -> i.getArgument(0));
        return new JobRunner(runs, mock(PlatformTransactionManager.class), mock(JdbcClient.class),
                Clock.fixed(Instant.parse("2026-11-02T04:00:00Z"), ZoneOffset.UTC), mock(ObjectProvider.class),
                events::add);
    }

    private final RunnableJob job = new RunnableJob() {
        @Override
        public String jobName() {
            return "region-maintenance";
        }

        @Override
        public void runNow() {
            if (failing) {
                throw new IllegalStateException("boom");
            }
        }
    };

    @Test
    void secondConsecutiveFailurePublishesOnce() {
        JobRunner runner = runner();
        failing = true;
        runner.run(job, JobRun.Trigger.SCHEDULE, null);
        assertThat(events).isEmpty();
        runner.run(job, JobRun.Trigger.SCHEDULE, null);
        runner.run(job, JobRun.Trigger.SCHEDULE, null);
        assertThat(events).hasSize(1).first().isInstanceOfSatisfying(JobRunner.JobFailedEvent.class, event -> {
            assertThat(event.jobName()).isEqualTo("region-maintenance");
            assertThat(event.message()).contains("boom");
        });
        failing = false;
        runner.run(job, JobRun.Trigger.SCHEDULE, null);
        failing = true;
        runner.run(job, JobRun.Trigger.SCHEDULE, null);
        assertThat(events).hasSize(1);
        runner.run(job, JobRun.Trigger.SCHEDULE, null);
        assertThat(events).hasSize(2);
    }
}
