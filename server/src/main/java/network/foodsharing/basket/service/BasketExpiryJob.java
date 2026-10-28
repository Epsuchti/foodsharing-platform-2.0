package network.foodsharing.basket.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily: ACTIVE baskets past {@code expires_at} become EXPIRED (B1, upstream basket clean-up cron). */
@Component
public class BasketExpiryJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(BasketExpiryJob.class);

    private final JobRunner jobRunner;
    private final BasketService baskets;

    public BasketExpiryJob(JobRunner jobRunner, BasketService baskets) {
        this.jobRunner = jobRunner;
        this.baskets = baskets;
    }

    @Scheduled(cron = "${app.basket.expiry-cron:0 20 3 * * *}", zone = "Europe/Zurich")
    @SchedulerLock(name = "basket-expiry", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "basket-expiry";
    }

    @Override
    public void runNow() {
        int expired = baskets.expire();
        if (expired > 0) {
            log.info("Basket expiry marked {} baskets EXPIRED", expired);
        }
    }
}
