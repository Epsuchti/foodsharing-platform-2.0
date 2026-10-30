package network.foodsharing.testsupport;

import network.foodsharing.auth.service.AuthRecordsCleanup;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.IpHasher;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.history.service.HistoryAccessPolicy;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryRetentionService;
import network.foodsharing.history.service.SystemHistoryAccessRule;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.region.RegionSliceConfig;
import network.foodsharing.region.service.RegionMaintenanceJob;
import network.foodsharing.security.SessionCookieWriter;
import network.foodsharing.user.service.UserArchiveCleanup;
import network.foodsharing.user.service.UserHistoryAccessRule;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The one bean set of all {@link JpaSliceTest}s without {@code @MockitoBean}s or own properties (docs/conventions.md
 * A60): they share a single Spring context and H2 database instead of starting one each. Every test still runs in its
 * own rolled-back transaction. Add the services a new slice test needs here rather than a class-specific
 * {@code @Import}; no event listeners that react to recorded events (the slices only record them).
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({RegionSliceConfig.class, AuthSessionService.class, SessionCookieWriter.class, IpHasher.class,
        AuthRecordsCleanup.class, HistoryQueryService.class, HistoryAccessPolicy.class, UserHistoryAccessRule.class,
        SystemHistoryAccessRule.class, HistoryRetentionService.class, BellService.class, RegionMaintenanceJob.class,
        JobRunner.class, UserArchiveCleanup.class})
public class SharedJpaSliceConfig {
}
