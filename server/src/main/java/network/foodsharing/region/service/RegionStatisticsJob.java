package network.foodsharing.region.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly region statistics cache ({@code app.region.statistics-cron}; dev name {@code region-statistics}). */
@Component
public class RegionStatisticsJob implements RunnableJob {

    private final JobRunner jobRunner;

    private static final Logger log = LoggerFactory.getLogger(RegionStatisticsJob.class);

    private final RegionStatisticsService statistics;

    public RegionStatisticsJob(JobRunner jobRunner, RegionStatisticsService statistics) {
        this.jobRunner = jobRunner;
        this.statistics = statistics;
    }

    @Scheduled(cron = "${app.region.statistics-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "region-statistics", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "region-statistics";
    }

    @Override
    public void runNow() {
        log.info("Refreshed statistics of {} regions", statistics.refreshAll());
    }
}
