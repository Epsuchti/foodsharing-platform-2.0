package network.foodsharing.poll.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code poll-notifications} every 15 min: POLL_STARTED and POLL_ENDING_SOON ({@link PollNotificationService}). */
@Component
public class PollNotificationJob implements RunnableJob {

    private final JobRunner jobRunner;
    private final PollNotificationService service;

    public PollNotificationJob(JobRunner jobRunner, PollNotificationService service) {
        this.jobRunner = jobRunner;
        this.service = service;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Zurich")
    @SchedulerLock(name = "poll-notifications", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "poll-notifications";
    }

    @Override
    public String schedule() {
        return "every 15 min";
    }

    @Override
    public void runNow() {
        service.notifyDue();
    }
}
