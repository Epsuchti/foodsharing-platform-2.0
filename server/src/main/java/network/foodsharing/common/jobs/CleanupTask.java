package network.foodsharing.common.jobs;

import java.time.Instant;

/**
 * One part of the nightly {@code cleanup} job (§6.3), contributed by the module that owns the data (so the job
 * itself depends on no module). Each task runs in its own transaction.
 */
public interface CleanupTask {

    /** Short name for the log line, e.g. {@code push-subscriptions}. */
    String name();

    /** Deletes what expired at {@code now}; @return number of removed rows. */
    int cleanup(Instant now);
}
