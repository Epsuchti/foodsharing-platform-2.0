package network.foodsharing.quiz.service;

import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily hygiene certificate expiry warnings (plan §6.3; dev name {@code hygiene-expiry}). */
@Component
public class HygieneExpiryJob implements RunnableJob {

    private final QuizNotifications notifications;

    public HygieneExpiryJob(QuizNotifications notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Zurich")
    public void run() {
        runNow();
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
