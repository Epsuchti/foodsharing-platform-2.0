package network.foodsharing.user.service;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import network.foodsharing.common.jobs.CleanupTask;
import network.foodsharing.history.NoHistory;
import network.foodsharing.user.repository.UserArchiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User part of the {@code cleanup} job (D17.3): the archive snapshot of a deleted account (name, email, phone,
 * address) is kept 2 years for support and abuse cases, like the {@code ACCOUNT_DELETED} history entry.
 */
@Service
public class UserArchiveCleanup implements CleanupTask {

    static final Period ARCHIVE_RETENTION = Period.ofYears(2);

    private final UserArchiveRepository archives;

    public UserArchiveCleanup(UserArchiveRepository archives) {
        this.archives = archives;
    }

    @Override
    public String name() {
        return "user-archive";
    }

    @Override
    @Transactional
    @NoHistory(reason = "retention housekeeping of deleted-account archives")
    public int cleanup(Instant now) {
        return archives.deleteCreatedBefore(now.atOffset(ZoneOffset.UTC).minus(ARCHIVE_RETENTION).toInstant());
    }
}
