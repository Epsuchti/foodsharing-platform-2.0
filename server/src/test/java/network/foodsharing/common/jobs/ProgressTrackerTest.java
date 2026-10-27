package network.foodsharing.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import network.foodsharing.config.MutableClock;
import org.junit.jupiter.api.Test;

/** Job lifecycle and the result TTL. */
class ProgressTrackerTest {

    private final MutableClock clock = new MutableClock(java.time.Clock.systemUTC(),
            Instant.parse("2026-11-02T09:00:00Z"));
    private final ProgressTracker tracker = new ProgressTracker(clock, Duration.ofMinutes(60),
            Executors.newSingleThreadExecutor());

    @Test
    void runNowTracksProgressAndResult() {
        UUID owner = UUID.randomUUID();
        UUID job = tracker.runNow(owner, 2, progress -> {
            progress.increment();
            progress.total(3);
            progress.done(3);
            return new ProgressTracker.JobResult(new byte[] {1}, "application/pdf", "x.pdf");
        });
        ProgressTracker.JobState state = tracker.find(job).orElseThrow();
        assertThat(state.status()).isEqualTo(ProgressTracker.Status.DONE);
        assertThat(state.total()).isEqualTo(3);
        assertThat(state.done()).isEqualTo(3);
        assertThat(state.ownerId()).isEqualTo(owner);
        assertThat(state.result().fileName()).isEqualTo("x.pdf");
    }

    @Test
    void finishedJobsExpireAfterTheTtlRunningOnesStay() {
        UUID done = tracker.runNow(UUID.randomUUID(), 1, progress -> null);
        UUID running = tracker.start(UUID.randomUUID(), 5);
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(61)));
        assertThat(tracker.find(done)).isEmpty();
        assertThat(tracker.find(running)).isPresent();
    }
}
