package network.foodsharing.onboarding.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily stalled-newcomer nudges at 09:00 Zurich (dev name {@code onboarding-nudges}). */
@Component
public class OnboardingNudgeJob implements RunnableJob {

    private final JobRunner jobRunner;

    private final OnboardingNudgeService nudges;

    public OnboardingNudgeJob(JobRunner jobRunner, OnboardingNudgeService nudges) {
        this.jobRunner = jobRunner;
        this.nudges = nudges;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Zurich")
    @SchedulerLock(name = "onboarding-nudges", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "onboarding-nudges";
    }

    @Override
    public void runNow() {
        nudges.sendStalledNudges();
    }
}
