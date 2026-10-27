package network.foodsharing.onboarding.service;

import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily stalled-newcomer nudges at 09:00 Zurich (dev name {@code onboarding-nudges}). */
@Component
public class OnboardingNudgeJob implements RunnableJob {

    private final OnboardingNudgeService nudges;

    public OnboardingNudgeJob(OnboardingNudgeService nudges) {
        this.nudges = nudges;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Zurich")
    public void run() {
        runNow();
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
