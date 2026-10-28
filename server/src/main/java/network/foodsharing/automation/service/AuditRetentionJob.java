package network.foodsharing.automation.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code audit-retention} (nightly): archive automation audits as anonymous daily counts, then delete them (AU7). */
@Component
public class AuditRetentionJob implements RunnableJob {

    static final String NAME = "audit-retention";

    private final JobRunner jobRunner;
    private final MetricArchiveService runner;
    private final AppProperties properties;

    public AuditRetentionJob(JobRunner jobRunner, MetricArchiveService runner, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.runner = runner;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.jobs.audit-retention-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "audit-retention", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    public String schedule() {
        return properties.getJobs().getAuditRetentionCron();
    }

    @Override
    public void runNow() {
        runner.archiveAndDelete();
    }
}
