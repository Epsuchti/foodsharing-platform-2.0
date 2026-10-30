package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.history.service.HistoryAccessPolicy;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.SystemHistoryAccessRule;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.SharedJpaSliceConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.service.UserHistoryAccessRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

@JpaSliceTest
@Import(SharedJpaSliceConfig.class)
class HistoryQueryServiceTest {

    private static final Instant NOW = FixedClockConfig.SEED_NOW;
    private static final UUID ME = SeedUsers.FOODSAVER.id();
    private static final UUID OTHER = SeedUsers.NEWBIE.id();

    @Autowired
    private HistoryQueryService service;

    @Autowired
    private HistoryEntryRepository repository;

    private HistoryEntry myLogin;
    private HistoryEntry myOldLogout;
    private HistoryEntry othersLogin;
    private HistoryEntry iChangedOther;
    private HistoryEntry otherMentionsMe;
    private HistoryEntry system;

    @BeforeEach
    void setUp() {
        myLogin = save(entry(SubjectType.USER, ME, "LOGIN", ME, NOW.minus(Duration.ofHours(1))));
        myOldLogout = save(entry(SubjectType.USER, ME, "LOGOUT", ME, NOW.minus(Duration.ofDays(10))));
        othersLogin = save(entry(SubjectType.USER, OTHER, "LOGIN", OTHER, NOW.minus(Duration.ofHours(2))));
        iChangedOther = save(entry(SubjectType.USER, OTHER, "ROLE_CHANGED", ME, NOW.minus(Duration.ofHours(3))));
        HistoryEntry mention = entry(SubjectType.STORE, UUID.randomUUID(), "MEMBER_ADDED", OTHER,
                NOW.minus(Duration.ofHours(4)));
        mention.setSecondarySubjectId(ME);
        otherMentionsMe = save(mention);
        system = save(entry(SubjectType.SYSTEM, new UUID(0, 0), "REGISTRATION_BLOCKED", null, NOW));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void usersReadTheirOwnHistoryNewestFirst() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        Page<HistoryEntry> page = service.list(HistoryFilter.forSubject(SubjectType.USER, ME, List.of(), 0, 20));

        assertThat(page.getContent()).containsExactly(myLogin, myOldLogout);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void filtersByActionsAndTimeRange() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        assertThat(service.listForSubject(SubjectType.USER, ME, List.of("LOGOUT"), 0, 20).getContent())
                .containsExactly(myOldLogout);
        HistoryFilter lastDay = new HistoryFilter(SubjectType.USER, ME, null, null, List.of(),
                NOW.minus(Duration.ofDays(1)), NOW, 0, 20);
        assertThat(service.list(lastDay).getContent()).containsExactly(myLogin);
        HistoryFilter toIsExclusive = new HistoryFilter(SubjectType.USER, ME, null, null, List.of(), null,
                myLogin.getOccurredAt(), 0, 20);
        assertThat(service.list(toIsExclusive).getContent()).containsExactly(myOldLogout);
    }

    @Test
    void pagesAreLimited() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        Page<HistoryEntry> first = service.list(HistoryFilter.forSubject(SubjectType.USER, ME, List.of(), 0, 1));
        Page<HistoryEntry> second = service.list(HistoryFilter.forSubject(SubjectType.USER, ME, List.of(), 1, 1));

        assertThat(first.getContent()).containsExactly(myLogin);
        assertThat(second.getContent()).containsExactly(myOldLogout);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(HistoryFilter.forSubject(SubjectType.USER, ME, List.of(), -1, 1000).size()).isEqualTo(100);
    }

    @Test
    void otherUsersHistoryIsNotReadable() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        assertForbidden(() -> service.list(HistoryFilter.forSubject(SubjectType.USER, OTHER, List.of(), 0, 20)),
                ApiErrorCode.HISTORY_SUBJECT_NOT_READABLE);
        assertForbidden(() -> service.list(HistoryFilter.forSubject(SubjectType.SYSTEM, new UUID(0, 0), List.of(), 0, 20)),
                ApiErrorCode.HISTORY_SUBJECT_NOT_READABLE);
    }

    @Test
    void queriesWithoutASubjectAreOrgaOnly() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));
        HistoryFilter byActor = new HistoryFilter(null, null, ME, null, List.of(), null, null, 0, 20);
        assertForbidden(() -> service.list(byActor), ApiErrorCode.HISTORY_SUBJECT_NOT_READABLE);

        TestAuth.login(TestAuth.principal(SeedUsers.ORGA));
        assertThat(service.list(byActor).getContent()).containsExactly(myLogin, iChangedOther, myOldLogout);
        HistoryFilter all = new HistoryFilter(null, null, null, null, List.of(), null, null, 0, 20);
        assertThat(service.list(all).getTotalElements()).isEqualTo(6);
        assertThat(service.list(HistoryFilter.forSubject(SubjectType.SYSTEM, new UUID(0, 0), List.of(), 0, 20))
                .getContent()).containsExactly(system);
    }

    @Test
    void unauthenticatedCallsAreRejected() {
        assertThatThrownBy(() -> service.list(HistoryFilter.forSubject(SubjectType.USER, ME, List.of(), 0, 20)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void myActivityContainsWhatIDidAndWhatConcernsMe() {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        Page<HistoryEntry> page = service.myActivity(new HistoryFilter(SubjectType.SYSTEM, new UUID(0, 0), OTHER,
                null, List.of(), null, null, 0, 20));

        // subject/actor filters of the request are ignored: always "my" activity
        assertThat(page.getContent()).containsExactly(myLogin, iChangedOther, otherMentionsMe, myOldLogout);
        assertThat(page.getContent()).doesNotContain(othersLogin, system);
    }

    @Test
    void exportIsOrgaOnly() {
        TestAuth.login(TestAuth.principal(SeedUsers.WELCOME));
        HistoryFilter filter = new HistoryFilter(null, null, null, null, List.of("LOGIN"), null, null, 0, 1);
        assertForbidden(() -> service.export(filter), ApiErrorCode.ACCESS_DENIED);

        TestAuth.login(TestAuth.principal(SeedUsers.ORGA));
        assertThat(service.export(filter)).containsExactly(myLogin, othersLogin);
    }

    @Test
    void regionLogAddsTheWorkingGroupChangesOfTheRegion() {
        UUID region = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        UUID otherRegion = UUID.randomUUID();
        HistoryEntry optionChanged = save(entry(SubjectType.REGION, region, "OPTION_CHANGED", ME,
                NOW.minus(Duration.ofHours(1))));
        HistoryEntry groupCreated = entry(SubjectType.GROUP, group, "CREATED", ME, NOW.minus(Duration.ofHours(2)));
        groupCreated.setRegionId(region);
        save(groupCreated);
        HistoryEntry otherGroup = entry(SubjectType.GROUP, UUID.randomUUID(), "CREATED", ME,
                NOW.minus(Duration.ofHours(3)));
        otherGroup.setRegionId(otherRegion);
        save(otherGroup);
        HistoryEntry otherRegionEntry = save(entry(SubjectType.REGION, otherRegion, "OPTION_CHANGED", ME,
                NOW.minus(Duration.ofHours(4))));

        TestAuth.login(TestAuth.principal(SeedUsers.ORGA));
        assertThat(service.regionLog(HistoryFilter.forSubject(SubjectType.REGION, region, List.of(), 0, 20))
                .getContent()).containsExactly(optionChanged, groupCreated).doesNotContain(otherGroup, otherRegionEntry);
        assertThat(service.regionLog(HistoryFilter.forSubject(SubjectType.REGION, region, List.of("CREATED"), 0, 20))
                .getContent()).containsExactly(groupCreated);
        // a working group's own page lists only its GROUP entries
        assertThat(service.regionLog(HistoryFilter.forSubject(SubjectType.GROUP, group, List.of(), 0, 20))
                .getContent()).containsExactly(groupCreated);

        // the subject's access rule still decides (no REGION rule for a plain foodsaver)
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));
        assertForbidden(() -> service.regionLog(HistoryFilter.forSubject(SubjectType.REGION, region, List.of(), 0, 20)),
                ApiErrorCode.HISTORY_SUBJECT_NOT_READABLE);
    }

    private static void assertForbidden(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getCode()).isEqualTo(code);
        });
    }

    private HistoryEntry save(HistoryEntry entry) {
        return repository.saveAndFlush(entry);
    }

    static HistoryEntry entry(SubjectType type, UUID subjectId, String action, UUID actorId, Instant occurredAt) {
        HistoryEntry entry = new HistoryEntry();
        entry.setSubjectType(type);
        entry.setSubjectId(subjectId);
        entry.setAction(action);
        entry.setActorId(actorId);
        entry.setActorType(actorId == null ? ActorType.SYSTEM : ActorType.USER);
        entry.setOccurredAt(occurredAt);
        return entry;
    }
}
