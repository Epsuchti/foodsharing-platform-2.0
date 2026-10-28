package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.history.service.HistoryActionRegistry;
import network.foodsharing.history.service.HistoryRetentionJob;
import network.foodsharing.history.service.HistoryRetentionService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.user.domain.UserAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@JpaSliceTest
@Import(HistoryRetentionService.class)
class HistoryRetentionServiceTest {

    private static final Instant NOW = FixedClockConfig.SEED_NOW;
    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private HistoryRetentionService service;

    @Autowired
    private HistoryEntryRepository repository;

    @Autowired
    private HistoryActionRegistry registry;

    @Test
    void deletesOnlyEntriesPastTheirActionsRetention() {
        // DAYS_90
        HistoryEntry oldFailedLogin = save(AuthAction.LOGIN_FAILED, NOW.minus(Duration.ofDays(91)));
        HistoryEntry recentFailedLogin = save(AuthAction.LOGIN_FAILED, NOW.minus(Duration.ofDays(89)));
        // YEARS_2
        HistoryEntry oldLogin = save(AuthAction.LOGIN, NOW.minus(Duration.ofDays(731)));
        HistoryEntry recentLogin = save(AuthAction.LOGIN, NOW.minus(Duration.ofDays(700)));
        // ACCOUNT_LIFETIME: never deleted by the job
        HistoryEntry ancientSettings = save(UserAction.SETTINGS_CHANGED, NOW.minus(Duration.ofDays(5000)));

        int deleted = service.purgeExpired();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.findAll())
                .containsExactlyInAnyOrder(recentFailedLogin, recentLogin, ancientSettings)
                .doesNotContain(oldFailedLogin, oldLogin);
    }

    @Test
    void sameCodeOfAnotherSubjectTypeIsNotAffected() {
        HistoryEntry storeEntry = HistoryQueryServiceTest.entry(SubjectType.STORE, USER, "LOGIN_FAILED", null,
                NOW.minus(Duration.ofDays(1000)));
        repository.saveAndFlush(storeEntry);

        assertThat(service.purgeExpired()).isZero();
        assertThat(repository.findAll()).containsExactly(storeEntry);
    }

    @Test
    void theRegistryKnowsTheActionsOfAllModules() {
        assertThat(registry.find(SubjectType.USER, "LOGIN")).contains(AuthAction.LOGIN);
        assertThat(registry.find(SubjectType.USER, "FIELD_CHANGED")).contains(UserAction.FIELD_CHANGED);
        assertThat(registry.find(SubjectType.SYSTEM, "REGISTRATION_BLOCKED")).contains(AuthAction.REGISTRATION_BLOCKED);
        assertThat(registry.find(SubjectType.SYSTEM, "LOGIN")).isEmpty();
        assertThat(registry.forSubjectType(SubjectType.USER)).contains(AuthAction.LOGOUT, UserAction.ACCOUNT_CREATED);
    }

    @Test
    void theJobOnlyRunsWhenRetentionIsEnabled() {
        HistoryRetentionService retention = mock(HistoryRetentionService.class);
        AppProperties properties = new AppProperties();
        HistoryRetentionJob job = new HistoryRetentionJob(mock(JobRunner.class), retention, properties);

        properties.getHistory().setRetentionEnabled(false);
        job.runNow();
        verify(retention, never()).purgeExpired();

        properties.getHistory().setRetentionEnabled(true);
        job.runNow();
        verify(retention).purgeExpired();
    }

    private HistoryEntry save(HistoryAction action, Instant occurredAt) {
        return repository.saveAndFlush(HistoryQueryServiceTest.entry(action.subjectType(), USER, action.code(), USER,
                occurredAt));
    }
}
