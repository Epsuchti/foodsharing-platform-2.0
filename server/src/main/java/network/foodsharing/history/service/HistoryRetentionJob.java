package network.foodsharing.history.service;

import network.foodsharing.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly history retention (ShedLock is added with the other jobs in milestone 4). */
@Component
public class HistoryRetentionJob {

    private final HistoryRetentionService retentionService;
    private final AppProperties properties;

    public HistoryRetentionJob(HistoryRetentionService retentionService, AppProperties properties) {
        this.retentionService = retentionService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.history.retention-cron}")
    public void run() {
        if (properties.getHistory().isRetentionEnabled()) {
            retentionService.purgeExpired();
        }
    }
}
