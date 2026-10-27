package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.MutableClock;
import network.foodsharing.config.TraceIdFilter;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.domain.UserAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class HistoryServiceTest {

    private static final UUID SUBJECT = UUID.fromString("00000001-0001-4000-8000-000000000099");

    private final HistoryEntryRepository repository = mock(HistoryEntryRepository.class);
    private final MutableClock clock = new MutableClock(Clock.systemUTC(), FixedClockConfig.SEED_NOW);
    @SuppressWarnings("unchecked")
    private final CurrentActorService currentActorService = new CurrentActorService(new PermissionService(),
            mock(ObjectProvider.class));
    private final HistoryService service = new HistoryService(repository, currentActorService, clock,
            JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        when(repository.save(any(HistoryEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        MDC.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void eventsOfASignedInUserHaveTheUserAsActor() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        HistoryEntry entry = service.record(service.event(UserAction.SETTINGS_CHANGED, SUBJECT).build());

        assertThat(entry.getActorType()).isEqualTo(ActorType.USER);
        assertThat(entry.getActorId()).isEqualTo(SeedUsers.FOODSAVER.id());
        assertThat(entry.getSubjectType()).isEqualTo(SubjectType.USER);
        assertThat(entry.getSubjectId()).isEqualTo(SUBJECT);
        assertThat(entry.getAction()).isEqualTo("SETTINGS_CHANGED");
        assertThat(entry.getOccurredAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        verify(repository).save(entry);
    }

    @Test
    void withoutASignedInUserTheSystemIsTheActor() {
        HistoryEntry entry = service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT).build());

        assertThat(entry.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(entry.getActorId()).isNull();
    }

    @Test
    void automationActorsKeepTheirNameInTheMetadata() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        HistoryEntry entry = service.toEntry(service.event(UserAction.ROLE_CHANGED, SUBJECT)
                .actorAutomation("quiz-promotion").metadata("quiz", "FOODSAVER").build());

        assertThat(entry.getActorType()).isEqualTo(ActorType.AUTOMATION);
        assertThat(entry.getActorId()).isNull();
        assertThat(entry.getMetadata()).isEqualTo("{\"quiz\":\"FOODSAVER\",\"automation\":\"quiz-promotion\"}");
    }

    @Test
    void copiesAllEventFieldsAndTheTraceId() {
        UUID secondary = UUID.randomUUID();
        UUID region = UUID.randomUUID();
        MDC.put(TraceIdFilter.MDC_KEY, "abcdef123456");

        HistoryEntry entry = service.toEntry(service.event(UserAction.SETTINGS_CHANGED, SUBJECT)
                .secondarySubject(secondary)
                .region(region)
                .change("preferredLanguage", Language.DE, Language.GWS)
                .metadata(Map.of("count", 2))
                .build());

        assertThat(entry.getSecondarySubjectId()).isEqualTo(secondary);
        assertThat(entry.getRegionId()).isEqualTo(region);
        assertThat(entry.getField()).isEqualTo("preferredLanguage");
        assertThat(entry.getOldValue()).isEqualTo("DE");
        assertThat(entry.getNewValue()).isEqualTo("GWS");
        assertThat(entry.getMetadata()).isEqualTo("{\"count\":2}");
        assertThat(entry.getTraceId()).isEqualTo("abcdef123456");
    }

    @Test
    void noMetadataIsStoredAsNull() {
        assertThat(service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT).build()).getMetadata()).isNull();
        assertThat(service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT).build()).getTraceId()).isNull();
    }

    @Test
    void theReasonComesFromTheEventOrElseFromTheHistoryContext() {
        HistoryEntry fromContext = HistoryContext.withReason("  asked by ambassador ",
                () -> service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT).build()));
        HistoryEntry explicit = HistoryContext.withReason("context",
                () -> service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT).reason("explicit").build()));
        HistoryEntry blank = HistoryContext.withReason("   ",
                () -> service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT).build()));

        assertThat(fromContext.getReason()).isEqualTo("asked by ambassador");
        assertThat(explicit.getReason()).isEqualTo("explicit");
        assertThat(blank.getReason()).isNull();
        assertThat(HistoryContext.reason()).isEmpty();
    }

    @Test
    void longValuesAreTruncated() {
        String huge = "x".repeat(HistoryEntry.MAX_VALUE_LENGTH + 10);
        HistoryEntry entry = service.toEntry(service.event(UserAction.SETTINGS_CHANGED, SUBJECT)
                .field("f".repeat(150)).oldValue(huge).newValue("short").reason("r".repeat(3000)).build());

        assertThat(entry.getField()).hasSize(100);
        assertThat(entry.getOldValue()).hasSize(HistoryEntry.MAX_VALUE_LENGTH).endsWith("…");
        assertThat(entry.getNewValue()).isEqualTo("short");
        assertThat(entry.getReason()).hasSize(HistoryEntry.MAX_REASON_LENGTH);
    }

    @Test
    void tooLargeMetadataIsRejected() {
        String huge = "x".repeat(HistoryEntry.MAX_VALUE_LENGTH);
        assertThatThrownBy(() -> service.toEntry(service.event(UserAction.DEACTIVATED, SUBJECT)
                .metadata("blob", huge).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitFieldRecordsAreRememberedForTheCurrentTransactionOnly() {
        assertThat(service.wasRecordedExplicitly(SubjectType.USER, SUBJECT, "displayName")).isFalse();

        TransactionSynchronizationManager.initSynchronization();
        service.record(service.event(UserAction.SETTINGS_CHANGED, SUBJECT).change("displayName", "a", "b").build());
        service.record(service.event(UserAction.DEACTIVATED, SUBJECT).build());

        assertThat(service.wasRecordedExplicitly(SubjectType.USER, SUBJECT, "displayName")).isTrue();
        assertThat(service.wasRecordedExplicitly(SubjectType.USER, SUBJECT, "firstName")).isFalse();
        assertThat(service.wasRecordedExplicitly(SubjectType.USER, UUID.randomUUID(), "displayName")).isFalse();

        // transaction completes: the remembered fields are released
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        assertThat(service.wasRecordedExplicitly(SubjectType.USER, SUBJECT, "displayName")).isFalse();
    }

    @Test
    void recordInNewTransactionPersistsTheEntry() {
        HistoryEntry entry = service.recordInNewTransaction(service.event(UserAction.DEACTIVATED, SUBJECT).build());
        verify(repository).save(entry);
    }
}
