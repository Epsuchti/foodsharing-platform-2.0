package network.foodsharing.region.service;

import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly region statistics cache ({@code app.region.statistics-cron}; dev name {@code region-statistics}). */
@Component
public class RegionStatisticsJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(RegionStatisticsJob.class);

    private final RegionStatisticsService statistics;

    public RegionStatisticsJob(RegionStatisticsService statistics) {
        this.statistics = statistics;
    }

    @Scheduled(cron = "${app.region.statistics-cron}", zone = "Europe/Zurich")
    public void run() {
        runNow();
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
