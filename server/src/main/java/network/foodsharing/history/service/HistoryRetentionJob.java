package network.foodsharing.history.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly history retention (ShedLock is added with the other jobs in milestone 4). */
@Component
public class HistoryRetentionJob implements RunnableJob {

    private final JobRunner jobRunner;

    private final HistoryRetentionService retentionService;
    private final AppProperties properties;

    public HistoryRetentionJob(JobRunner jobRunner, HistoryRetentionService retentionService, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.retentionService = retentionService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.history.retention-cron}")
    @SchedulerLock(name = "history-retention", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "history-retention";
    }

    @Override
    public void runNow() {
        if (properties.getHistory().isRetentionEnabled()) {
            retentionService.purgeExpired();
        }
    }
}
