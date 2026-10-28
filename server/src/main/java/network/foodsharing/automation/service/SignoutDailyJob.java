package network.foodsharing.automation.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code signout-alerts} (hourly): daily outcome of short-notice sign-outs per store (AU5). */
@Component
public class SignoutDailyJob implements RunnableJob {

    static final String NAME = "signout-alerts";

    private final JobRunner jobRunner;
    private final SignoutAlertService runner;
    private final AppProperties properties;

    public SignoutDailyJob(JobRunner jobRunner, SignoutAlertService runner, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.runner = runner;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.automation.signout-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "signout-alerts", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    public String schedule() {
        return properties.getAutomation().getSignoutCron();
    }

    @Override
    public void runNow() {
        runner.runDaily();
    }
}
