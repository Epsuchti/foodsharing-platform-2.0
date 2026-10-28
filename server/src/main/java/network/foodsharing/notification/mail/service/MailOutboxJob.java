package network.foodsharing.notification.mail.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers due outbox mails every 10 s (off in the test profile; tests call {@link MailOutboxService#processDue}). */
@Component
public class MailOutboxJob implements RunnableJob {

    private final JobRunner jobRunner;
    private final IntegrationRegistry integrations;

    private final MailOutboxService outbox;

    public MailOutboxJob(JobRunner jobRunner, MailOutboxService outbox, IntegrationRegistry integrations) {
        this.jobRunner = jobRunner;
        this.integrations = integrations;
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${app.mail.outbox-interval-ms:10000}", initialDelayString = "${app.mail.outbox-interval-ms:10000}")
    @SchedulerLock(name = "mail-outbox", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "mail-outbox";
    }

    @Override
    public void runNow() {
        if (integrations.shouldSkip(Integration.SMTP)) {
            return; // A26: SMTP is down, mails stay PENDING without using up attempts
        }
        outbox.processDue();
    }

    @Override
    public boolean recordEveryRun() {
        return false;
    }
}
