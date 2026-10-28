package network.foodsharing.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import network.foodsharing.config.AppProperties;
import org.junit.jupiter.api.Test;

/** §6.3 cleanup: every task runs with the clock's now, job_run rows older than 90 days go, failures fail the run. */
class CleanupJobTest {

    static final Instant NOW = Instant.parse("2026-11-02T04:00:00Z");

    private final List<Instant> seen = new ArrayList<>();

    private CleanupTask task(String name, boolean fail) {
        return new CleanupTask() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int cleanup(Instant now) {
                seen.add(now);
                if (fail) {
                    throw new IllegalStateException("boom");
                }
                return 3;
            }
        };
    }

    private CleanupJob job(JobRunner runner, CleanupTask... tasks) {
        return new CleanupJob(runner, List.of(tasks), mock(ProgressTracker.class), new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runsEveryTaskAndPurgesOldJobRuns() {
        JobRunner runner = mock(JobRunner.class);
        when(runner.purgeBefore(NOW.minus(Duration.ofDays(90)))).thenReturn(4);
        CleanupJob job = job(runner, task("a", false), task("b", false));
        job.runNow();
        assertThat(seen).containsExactly(NOW, NOW);
        verify(runner).purgeBefore(NOW.minus(Duration.ofDays(90)));
        assertThat(job.jobName()).isEqualTo("cleanup");
    }

    @Test
    void aFailingTaskDoesNotStopTheOthersButFailsTheRun() {
        JobRunner runner = mock(JobRunner.class);
        CleanupJob job = job(runner, task("a", true), task("b", false));
        assertThatThrownBy(job::runNow).hasMessageContaining("a");
        assertThat(seen).hasSize(2);
        verify(runner).purgeBefore(NOW.minus(Duration.ofDays(90)));
    }
}
