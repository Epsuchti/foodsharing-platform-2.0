package network.foodsharing.statistics.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly platform totals ({@code app.statistics.cron}; dev name {@code statistics-aggregation}). */
@Component
public class StatisticsAggregationJob implements RunnableJob {

    private final JobRunner jobRunner;
    private final StatisticsAggregationService aggregation;
    private final PublicStatisticsService publicStatistics;

    public StatisticsAggregationJob(JobRunner jobRunner, StatisticsAggregationService aggregation,
                                    PublicStatisticsService publicStatistics) {
        this.jobRunner = jobRunner;
        this.aggregation = aggregation;
        this.publicStatistics = publicStatistics;
    }

    @Scheduled(cron = "${app.statistics.cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "statistics-aggregation", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @Override
    public String jobName() {
        return "statistics-aggregation";
    }

    @Override
    public void runNow() {
        aggregation.aggregate();
        publicStatistics.evict();
    }
}
