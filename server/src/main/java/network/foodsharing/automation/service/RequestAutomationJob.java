package network.foodsharing.automation.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code request-automation} (every 5 min): open applications of stores with an enabled request policy. */
@Component
public class RequestAutomationJob implements RunnableJob {

    static final String NAME = "request-automation";

    private final JobRunner jobRunner;
    private final RequestPolicyRunner runner;
    private final AppProperties properties;

    public RequestAutomationJob(JobRunner jobRunner, RequestPolicyRunner runner, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.runner = runner;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.automation.evaluate-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "request-automation", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return NAME;
    }

    @Override
    public String schedule() {
        return properties.getAutomation().getEvaluateCron();
    }

    @Override
    public void runNow() {
        PickupPolicyRunner.RunResult result = runner.runAll();
        if (result.failed() > 0) {
            throw new IllegalStateException(result.failed() + " request decisions failed: "
                    + String.join("; ", result.messages()));
        }
    }
}
