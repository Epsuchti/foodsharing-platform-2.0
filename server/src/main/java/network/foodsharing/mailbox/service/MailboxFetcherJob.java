package network.foodsharing.mailbox.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code mailbox-fetcher} (D6.3): every minute, inbound mails from the configured source (A35). */
@Component
public class MailboxFetcherJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(MailboxFetcherJob.class);

    private final JobRunner jobRunner;
    private final IncomingMailSource source;
    private final IncomingMailProcessor processor;
    private final IntegrationRegistry integrations;
    private final AppProperties properties;

    public MailboxFetcherJob(JobRunner jobRunner, IncomingMailSource source, IncomingMailProcessor processor,
                             IntegrationRegistry integrations, AppProperties properties) {
        this.jobRunner = jobRunner;
        this.source = source;
        this.processor = processor;
        this.integrations = integrations;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.mailbox.fetch-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "mailbox-fetcher", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "mailbox-fetcher";
    }

    @Override
    public String schedule() {
        return properties.getMailbox().getFetchCron();
    }

    @Override
    public boolean recordEveryRun() {
        return false;
    }

    /** Skips while the IMAP breaker is open; each message is processed in its own transaction. */
    @Override
    public void runNow() {
        if (integrations.shouldSkip(Integration.IMAP)) {
            log.debug("mailbox-fetcher skipped: IMAP breaker open");
            return;
        }
        int fetched = source.fetch(processor::process);
        if (fetched > 0) {
            log.info("mailbox-fetcher processed {} inbound mails", fetched);
        }
    }
}
