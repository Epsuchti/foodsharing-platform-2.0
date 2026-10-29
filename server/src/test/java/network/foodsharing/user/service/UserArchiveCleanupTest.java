package network.foodsharing.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.user.domain.UserArchive;
import network.foodsharing.user.repository.UserArchiveRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** D17.3 retention: the snapshot of a deleted account is kept 2 years. */
@JpaSliceTest
@Import(UserArchiveCleanup.class)
class UserArchiveCleanupTest {

    @Autowired
    private UserArchiveCleanup cleanup;

    @Autowired
    private UserArchiveRepository archives;

    @Autowired
    private Clock clock;

    @Autowired
    private jakarta.persistence.EntityManager em;

    @Test
    void archiveIsKeptTwoYears() {
        UserArchive archive = new UserArchive();
        archive.setUserId(UUID.randomUUID());
        archive.setSnapshotJson("{}");
        archive = archives.saveAndFlush(archive);

        assertThat(cleanup.cleanup(clock.instant().plus(Duration.ofDays(729)))).isZero();
        assertThat(archives.findById(archive.getId())).isPresent();
        assertThat(cleanup.cleanup(clock.instant().plus(Duration.ofDays(732)))).isEqualTo(1);
        em.clear();
        assertThat(archives.findById(archive.getId())).isEmpty();
    }
}
