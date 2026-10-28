package network.foodsharing.search.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Search index maintenance: nightly rebuild ({@code app.search.reindex-cron}, dev name {@code search-reindex}),
 * incremental refresh every {@code app.search.refresh-seconds} and a rebuild on start when the index is empty.
 */
@Component
public class SearchReindexJob implements RunnableJob {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexJob.class);

    private final JobRunner jobRunner;
    private final SearchIndexer indexer;

    public SearchReindexJob(JobRunner jobRunner, SearchIndexer indexer) {
        this.jobRunner = jobRunner;
        this.indexer = indexer;
    }

    @Scheduled(cron = "${app.search.reindex-cron}", zone = "Europe/Zurich")
    @SchedulerLock(name = "search-reindex", lockAtLeastFor = "PT5S")
    public void run() {
        jobRunner.run(this, JobRun.Trigger.SCHEDULE, null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        try {
            if (indexer.rebuildIfEmpty()) {
                log.info("Search index was empty and has been rebuilt");
            }
        } catch (RuntimeException e) {
            log.warn("Initial search index build failed: {}", e.getMessage());
        }
    }

    @Override
    public String jobName() {
        return "search-reindex";
    }

    @Override
    public void runNow() {
        log.info("Search index rebuilt with {} entries", indexer.rebuild());
    }
}
