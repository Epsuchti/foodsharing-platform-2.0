package network.foodsharing.quiz.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily hygiene certificate expiry warnings (plan §6.3; dev name {@code hygiene-expiry}). */
@Component
public class HygieneExpiryJob implements RunnableJob {

    private final JobRunner jobRunner;

    private final QuizNotifications notifications;

    public HygieneExpiryJob(JobRunner jobRunner, QuizNotifications notifications) {
        this.jobRunner = jobRunner;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Zurich")
    @SchedulerLock(name = "hygiene-expiry", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "hygiene-expiry";
    }

    @Override
    public void runNow() {
        notifications.remindHygieneExpiring();
    }
}
