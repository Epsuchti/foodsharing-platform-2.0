package network.foodsharing.automation.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code open-slot-adverts} (every 5 min): open-slot adverts of all stores (AU4). */
@Component
public class OpenSlotAdvertJob implements RunnableJob {

    static final String NAME = "open-slot-adverts";

    private final JobRunner jobRunner;
    private final OpenSlotAdvertRunner runner;
    private final AppProperties properties;

    public OpenSlotAdvertJob(JobRunner jobRunner, OpenSlotAdvertRunner runner, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.runner = runner;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.automation.adverts-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "open-slot-adverts", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    public String schedule() {
        return properties.getAutomation().getAdvertsCron();
    }

    @Override
    public void runNow() {
        PickupPolicyRunner.RunResult result = runner.runAll();
        if (result.failed() > 0) {
            throw new IllegalStateException(result.failed() + " adverts failed: " + String.join("; ",
                    result.messages()));
        }
    }
}
