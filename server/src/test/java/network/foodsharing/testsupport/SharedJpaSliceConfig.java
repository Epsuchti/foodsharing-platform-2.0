package network.foodsharing.testsupport;

import network.foodsharing.auth.service.AuthRecordsCleanup;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.IpHasher;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.history.service.HistoryAccessPolicy;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryRetentionService;
import network.foodsharing.history.service.SystemHistoryAccessRule;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.region.RegionSliceConfig;
import network.foodsharing.region.service.RegionMaintenanceJob;
import network.foodsharing.security.SessionCookieWriter;
import network.foodsharing.upload.repository.UploadBlobRepository;
import network.foodsharing.upload.service.DbUploadStorage;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.service.UserArchiveCleanup;
import network.foodsharing.user.service.UserHistoryAccessRule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The one bean set of all {@link JpaSliceTest}s without {@code @MockitoBean}s or own properties (docs/conventions.md
 * A60): they share a single Spring context and H2 database instead of starting one each. Every test still runs in its
 * own rolled-back transaction. Add the services a new slice test needs here rather than a class-specific
 * {@code @Import}; no event listeners that react to recorded events (the slices only record them). A test that needs
 * mocked collaborators builds its service with {@code new} from the shared beans and plain Mockito mocks (for example
 * {@code BasketServiceTest}) instead of {@code @MockitoBean}, which would start a context of its own. Put a changed
 * {@code MutableClock} back to {@code SEED_NOW} after each test.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({RegionSliceConfig.class, AuthSessionService.class, SessionCookieWriter.class, IpHasher.class,
        AuthRecordsCleanup.class, HistoryQueryService.class, HistoryAccessPolicy.class, UserHistoryAccessRule.class,
        SystemHistoryAccessRule.class, HistoryRetentionService.class, BellService.class, RegionMaintenanceJob.class,
        JobRunner.class, UserArchiveCleanup.class, UploadService.class, DbUploadStorage.class,
        UploadBlobRepository.class, LegalService.class, ContentService.class})
public class SharedJpaSliceConfig {
}
