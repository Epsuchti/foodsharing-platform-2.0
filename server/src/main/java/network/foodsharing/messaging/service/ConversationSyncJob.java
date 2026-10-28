package network.foodsharing.messaging.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly reconciliation of store and region chat members (missed events, A33). */
@Component
public class ConversationSyncJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationSyncJob.class);

    private final JobRunner jobRunner;
    private final StoreConversationSync stores;
    private final RegionConversationSync regions;

    public ConversationSyncJob(JobRunner jobRunner, StoreConversationSync stores, RegionConversationSync regions) {
        this.jobRunner = jobRunner;
        this.stores = stores;
        this.regions = regions;
    }

    @Scheduled(cron = "${app.messaging.sync-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "conversation-sync", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "conversation-sync";
    }

    @Override
    public String schedule() {
        return "hourly";
    }

    @Override
    public void runNow() {
        int changes = stores.syncAll() + regions.syncAll();
        if (changes > 0) {
            log.info("Conversation sync changed {} memberships", changes);
        }
    }
}
