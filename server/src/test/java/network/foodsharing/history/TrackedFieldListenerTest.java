package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Theme;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.service.SettingsChange;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@JpaSliceTest
@Import(UserService.class)
class TrackedFieldListenerTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private HistoryEntryRepository historyRepository;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = em.persistFlushFind(TestUsers.user("tracked@local"));
        TestAuth.login(TestUsers.principal(user));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void changingATrackedFieldRecordsFieldChangedWithOldAndNewValue() {
        user.setDisplayName("Renamed");
        em.flush();

        List<HistoryEntry> entries = fieldChanges();
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getAction()).isEqualTo(UserAction.FIELD_CHANGED.code());
            assertThat(entry.getSubjectType()).isEqualTo(SubjectType.USER);
            assertThat(entry.getSubjectId()).isEqualTo(user.getId());
            assertThat(entry.getField()).isEqualTo("displayName");
            assertThat(entry.getOldValue()).isEqualTo("tracked");
            assertThat(entry.getNewValue()).isEqualTo("Renamed");
            assertThat(entry.getActorType()).isEqualTo(ActorType.USER);
            assertThat(entry.getActorId()).isEqualTo(user.getId());
            assertThat(entry.getOccurredAt()).isEqualTo(FixedClockConfig.SEED_NOW);
        });
    }

    @Test
    void everyChangedTrackedFieldGetsItsOwnEntryAndEnumsAreStoredByName() {
        user.setFirstName("Anna");
        user.setLastName(null);
        user.setPreferredLanguage(Language.GWS);
        em.flush();

        assertThat(fieldChanges())
                .extracting(HistoryEntry::getField, HistoryEntry::getOldValue, HistoryEntry::getNewValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("firstName", "First", "Anna"),
                        org.assertj.core.groups.Tuple.tuple("lastName", "Last", null),
                        org.assertj.core.groups.Tuple.tuple("preferredLanguage", "DE", "GWS"));
    }

    @Test
    void untrackedFieldsAndUnchangedValuesRecordNothing() {
        user.setTheme(Theme.DARK);
        user.setAvatarUrl("https://example.org/a.png");
        user.setDisplayName("tracked");
        em.flush();

        assertThat(fieldChanges()).isEmpty();
    }

    @Test
    void theRequestReasonIsStoredWithTheChange() {
        HistoryContext.withReason("typo in name", () -> {
            user.setDisplayName("Fixed");
            em.flush();
        });

        assertThat(fieldChanges()).singleElement().extracting(HistoryEntry::getReason).isEqualTo("typo in name");
    }

    @Test
    void withoutASignedInUserTheSystemIsTheActor() {
        TestAuth.clear();
        user.setDisplayName("By system");
        em.flush();

        assertThat(fieldChanges()).singleElement().satisfies(entry -> {
            assertThat(entry.getActorType()).isEqualTo(ActorType.SYSTEM);
            assertThat(entry.getActorId()).isNull();
        });
    }

    @Test
    void anExplicitlyRecordedFieldIsNotRecordedTwice() {
        historyService.record(historyService.event(UserAction.ROLE_CHANGED, user.getId())
                .change("displayName", "tracked", "Explicit").build());
        user.setDisplayName("Explicit");
        user.setFirstName("Other");
        em.flush();

        assertThat(entries())
                .extracting(HistoryEntry::getAction, HistoryEntry::getField)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ROLE_CHANGED", "displayName"),
                        org.assertj.core.groups.Tuple.tuple("FIELD_CHANGED", "firstName"));
    }

    @Test
    void updateSettingsRecordsSettingsChangedOnlyOncePerField() {
        userService.updateSettings(new SettingsChange(Language.FR, Theme.DARK));
        em.flush();

        assertThat(entries())
                .extracting(HistoryEntry::getAction, HistoryEntry::getField, HistoryEntry::getOldValue,
                        HistoryEntry::getNewValue)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("SETTINGS_CHANGED", "preferredLanguage", "DE", "FR"),
                        org.assertj.core.groups.Tuple.tuple("SETTINGS_CHANGED", "theme", "SYSTEM", "DARK"));
    }

    @Test
    void updateSettingsWithUnchangedValuesRecordsNothing() {
        userService.updateSettings(new SettingsChange(Language.DE, null));
        em.flush();

        assertThat(entries()).isEmpty();
    }

    private List<HistoryEntry> entries() {
        return historyRepository.findAll();
    }

    private List<HistoryEntry> fieldChanges() {
        return entries().stream().filter(entry -> entry.getAction().equals(UserAction.FIELD_CHANGED.code())).toList();
    }
}
