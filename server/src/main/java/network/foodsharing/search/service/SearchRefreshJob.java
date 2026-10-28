package network.foodsharing.search.service;

import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Incremental search index refresh every {@code app.search.refresh-seconds} (dev name {@code search-refresh}). Runs
 * without a {@code job_run} row per tick; failures are logged and retried on the next tick.
 */
@Component
public class SearchRefreshJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(SearchRefreshJob.class);

    private final SearchIndexer indexer;

    public SearchRefreshJob(SearchIndexer indexer) {
        this.indexer = indexer;
    }

    @Scheduled(fixedDelayString = "${app.search.refresh-seconds}", initialDelayString = "${app.search.refresh-seconds}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "search-refresh", lockAtLeastFor = "PT5S")
    public void run() {
        try {
            runNow();
        } catch (RuntimeException e) {
            log.warn("Search refresh failed: {}", e.getMessage());
        }
    }

    @Override
    public String jobName() {
        return "search-refresh";
    }

    @Override
    public void runNow() {
        indexer.refresh();
    }
}
