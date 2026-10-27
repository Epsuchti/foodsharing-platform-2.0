package network.foodsharing.region.service;

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

    private static final Logger log = LoggerFactory.getLogger(RegionMaintenanceJob.class);

    private final MembershipService memberships;

    public RegionMaintenanceJob(MembershipService memberships) {
        this.memberships = memberships;
    }

    @Scheduled(cron = "${app.region.maintenance-cron}", zone = "Europe/Zurich")
    public void run() {
        runNow();
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
    }
}
