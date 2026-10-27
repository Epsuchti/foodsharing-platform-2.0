package network.foodsharing.common.jobs;

/**
 * A scheduled job that tests may also trigger by name ({@code POST /api/dev/jobs/{name}/run}, local/e2e only).
 * Implemented by the {@code *Job} classes, e.g. {@code region-maintenance}.
 */
public interface RunnableJob {

    /** Kebab-case name, unique across jobs. */
    String jobName();

    /** Runs one iteration now (same code as the schedule). */
    void runNow();
}
