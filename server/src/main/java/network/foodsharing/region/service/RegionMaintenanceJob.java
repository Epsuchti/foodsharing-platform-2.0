package network.foodsharing.region.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly region maintenance (plan §6.3): members of a region become members of its ancestors. Runs on
 * {@code app.region.maintenance-cron}; scheduling is off when {@code app.scheduling.enabled=false}. Single instance
 * until ShedLock arrives in M4.
 */
@Component
public class RegionMaintenanceJob implements RunnableJob {

    private final JobRunner jobRunner;

    private static final Logger log = LoggerFactory.getLogger(RegionMaintenanceJob.class);

    private final MembershipService memberships;

    private final network.foodsharing.user.service.UserService users;

    public RegionMaintenanceJob(JobRunner jobRunner, MembershipService memberships,
                                network.foodsharing.user.service.UserService users) {
        this.jobRunner = jobRunner;
        this.memberships = memberships;
        this.users = users;
    }

    @Scheduled(cron = "${app.region.maintenance-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "region-maintenance", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "region-maintenance";
    }

    @Override
    public void runNow() {
        int added = memberships.addMissingAncestorMemberships();
        if (added > 0) {
            log.info("Region maintenance added {} parent memberships", added);
        }
        int woken = users.wakeUpExpiredSleepers();
        if (woken > 0) {
            log.info("Region maintenance ended {} temporary sleep modes", woken);
        }
    }
}
