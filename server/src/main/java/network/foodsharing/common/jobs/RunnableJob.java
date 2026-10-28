package network.foodsharing.common.jobs;

/**
 * A scheduled job that tests may also trigger by name ({@code POST /api/dev/jobs/{name}/run}, local/e2e only; ORGA
 * {@code POST /api/admin/jobs/{name}/run}). Implemented by the {@code *Job} classes, e.g. {@code region-maintenance}.
 * <p>
 * A25: the {@code @Scheduled} method is called {@code run()}, carries {@code @SchedulerLock(name = <jobName()>)} and
 * delegates to {@link JobRunner#run(RunnableJob, JobRun.Trigger, java.util.UUID)}, which calls {@link #runNow()}.
 * {@link #runNow()} takes no lock, so tests and "run now" are re-entrant.
 */
public interface RunnableJob {

    /** Kebab-case name, unique across jobs; equal to the {@code @SchedulerLock} name. */
    String jobName();

    /** Runs one iteration now (same code as the schedule). */
    void runNow();

    /** Schedule shown on the admin page (cron expression or e.g. "every 10 s"). */
    default String schedule() {
        return null;
    }

    /**
     * Whether every scheduled run is written to {@code job_run}. High-frequency jobs (mail outbox, every 10 s)
     * return false: only their failures and manual runs are written, the last run is kept in memory.
     */
    default boolean recordEveryRun() {
        return true;
    }
}
