package network.foodsharing.history.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.repository.HistoryEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes entries past their action's retention ({@link RetentionPolicy#DAYS_90}, {@link RetentionPolicy#YEARS_2}).
 * FOREVER and ACCOUNT_LIFETIME entries are never deleted here; account deletion anonymises the ACCOUNT_LIFETIME
 * entries about the user ({@link #anonymiseAccountLifetime}).
 */
@Service
public class HistoryRetentionService {

    private static final Logger log = LoggerFactory.getLogger(HistoryRetentionService.class);

    private final HistoryEntryRepository repository;
    private final HistoryActionRegistry registry;
    private final Clock clock;

    public HistoryRetentionService(HistoryEntryRepository repository, HistoryActionRegistry registry, Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.clock = clock;
    }

    /** @return number of deleted entries */
    @Transactional
    public int purgeExpired() {
        Instant now = clock.instant();
        int deleted = 0;
        for (HistoryAction action : registry.all()) {
            Optional<Duration> keep = keepFor(action.retention());
            if (keep.isPresent()) {
                deleted += repository.deleteOlderThan(action.subjectType(), action.code(), now.minus(keep.get()));
            }
        }
        if (deleted > 0) {
            log.info("History retention deleted {} entries", deleted);
        }
        return deleted;
    }

    /**
     * Account deletion: entries of {@link RetentionPolicy#ACCOUNT_LIFETIME} actions whose subject or secondary
     * subject is the user lose their values, reason and metadata (action, time and actor stay). @return entries changed
     */
    @Transactional
    @NoHistory(reason = "part of the account deletion, which records UserAction.ACCOUNT_DELETED")
    public int anonymiseAccountLifetime(UUID userId) {
        int changed = 0;
        for (HistoryAction action : registry.all()) {
            if (action.retention() == RetentionPolicy.ACCOUNT_LIFETIME) {
                changed += repository.anonymise(action.subjectType(), action.code(), userId);
            }
        }
        return changed;
    }

    static Optional<Duration> keepFor(RetentionPolicy policy) {
        return switch (policy) {
            case DAYS_90 -> Optional.of(Duration.ofDays(90));
            case YEARS_2 -> Optional.of(ChronoUnit.YEARS.getDuration().multipliedBy(2));
            case FOREVER, ACCOUNT_LIFETIME -> Optional.empty();
        };
    }
}
