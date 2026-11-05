package network.foodsharing.intropickup.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Q4 transition (decision 168; dev name {@code guide-quiz-deadline}): daily 08:15 Zurich. Before the deadline it sends
 * the GUIDE_QUIZ_REQUIRED reminders, from the deadline on it revokes the guides without an EinAb guide quiz pass
 * ({@link GuideQuizTransitionService}).
 */
@Component
public class GuideQuizDeadlineJob implements RunnableJob {

    static final String CRON = "0 15 8 * * *";

    private final JobRunner jobRunner;
    private final GuideQuizTransitionService transition;

    public GuideQuizDeadlineJob(JobRunner jobRunner, GuideQuizTransitionService transition) {
        this.jobRunner = jobRunner;
        this.transition = transition;
    }

    @Scheduled(cron = CRON, zone = "Europe/Zurich")
    @SchedulerLock(name = "guide-quiz-deadline", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "guide-quiz-deadline";
    }

    @Override
    public String schedule() {
        return CRON;
    }

    @Override
    public void runNow() {
        if (transition.deadlineReached()) {
            transition.revokeWithoutPass();
        } else {
            transition.remind();
        }
    }
}
