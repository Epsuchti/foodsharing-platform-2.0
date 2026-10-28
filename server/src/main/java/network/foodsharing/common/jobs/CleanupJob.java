package network.foodsharing.common.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code cleanup} (§6.3, nightly): every {@link CleanupTask} (dead push subscriptions, expired login and Telegram link
 * tokens, old bells, delivered mails) plus {@code job_run} rows older than 90 days and expired job results. A failing
 * task does not stop the others; the run fails at the end so ORGA sees it.
 */
@Component
public class CleanupJob implements RunnableJob {

    static final Duration JOB_RUN_RETENTION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(CleanupJob.class);

    private final JobRunner jobRunner;
    private final List<CleanupTask> tasks;
    private final ProgressTracker progress;
    private final AppProperties properties;
    private final Clock clock;

    public CleanupJob(JobRunner jobRunner, List<CleanupTask> tasks, ProgressTracker progress, AppProperties properties,
                      Clock clock) {
        this.jobRunner = jobRunner;
        this.tasks = tasks;
        this.progress = progress;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.cleanup-cron}")
    @SchedulerLock(name = "cleanup", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "cleanup";
    }

    @Override
    public String schedule() {
        return properties.getJobs().getCleanupCron();
    }

    @Override
    public void runNow() {
        Instant now = clock.instant();
        List<String> failed = new ArrayList<>();
        for (CleanupTask task : tasks) {
            try {
                int removed = task.cleanup(now);
                log.info("cleanup {}: {} removed", task.name(), removed);
            } catch (RuntimeException ex) {
                failed.add(task.name());
                log.warn("cleanup {} failed", task.name(), ex);
            }
        }
        int runs = jobRunner.purgeBefore(now.minus(JOB_RUN_RETENTION));
        log.info("cleanup job-runs: {} removed", runs);
        progress.evictExpired();
        if (!failed.isEmpty()) {
            throw new IllegalStateException("cleanup tasks failed: " + String.join(", ", failed));
        }
    }
}
