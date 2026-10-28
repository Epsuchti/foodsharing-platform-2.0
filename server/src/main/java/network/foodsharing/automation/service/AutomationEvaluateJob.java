package network.foodsharing.automation.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code automation-evaluate} (every 5 min): re-evaluates every unconfirmed future sign-up of enabled pickup policies —
 * the cases deferred by the linked-store rule and sign-ups that came in while a policy was off. New sign-ups are decided
 * instantly by {@link PickupPolicyRunner#onSignedUp}.
 */
@Component
public class AutomationEvaluateJob implements RunnableJob {

    static final String NAME = "automation-evaluate";

    private final JobRunner jobRunner;
    private final PickupPolicyRunner runner;
    private final AppProperties properties;

    public AutomationEvaluateJob(JobRunner jobRunner, PickupPolicyRunner runner, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.runner = runner;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.automation.evaluate-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "automation-evaluate", lockAtLeastFor = "PT5S")
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
            throw new IllegalStateException(result.failed() + " pickup policy decisions failed: "
                    + String.join("; ", result.messages()));
        }
    }
}
