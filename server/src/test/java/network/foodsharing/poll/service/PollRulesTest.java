package network.foodsharing.poll.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.poll.domain.PollType;
import network.foodsharing.poll.service.PollRules.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A36 poll rules (upstream VotingPermissions / isValidVote). */
class PollRulesTest {

    static final UUID ME = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    @ParameterizedTest(name = "member={0} verified={1} group={2} admin={3} votingAdmin={4} -> {5}")
    @CsvSource({
            "true,  true,  false, false, true,  true",
            "true,  true,  false, true,  false, false",
            "true,  false, false, false, true,  false",
            "false, true,  false, false, true,  false",
            "true,  true,  true,  true,  false, true",
            "true,  true,  true,  false, true,  false",
    })
    void createMatrix(boolean member, boolean verified, boolean group, boolean admin, boolean votingAdmin,
                      boolean expected) {
        assertThat(PollRules.mayCreate(new PollRules.Actor(ME, false, member, verified, group, admin, votingAdmin)))
                .isEqualTo(expected);
    }

    @Test
    void statusAndPermissionsFollowTheDates() {
        Instant start = NOW.plusSeconds(3600);
        Instant end = NOW.plusSeconds(7200);
        assertThat(PollRules.status(start, end, null, NOW)).isEqualTo(Status.UPCOMING);
        assertThat(PollRules.status(start, end, null, start)).isEqualTo(Status.RUNNING);
        assertThat(PollRules.status(start, end, null, end)).isEqualTo(Status.ENDED);
        assertThat(PollRules.status(start, end, NOW, NOW)).isEqualTo(Status.CANCELLED);

        PollRules.Actor author = new PollRules.Actor(ME, false, true, true, false, false, true);
        PollRules.Actor orga = new PollRules.Actor(OTHER, true, false, true, false, false, false);
        assertThat(PollRules.mayEdit(author, ME, Status.UPCOMING)).isTrue();
        assertThat(PollRules.mayEdit(author, ME, Status.RUNNING)).isFalse();
        assertThat(PollRules.mayEdit(orga, ME, Status.UPCOMING)).isFalse();
        assertThat(PollRules.mayCancel(author, ME, Status.RUNNING)).isTrue();
        assertThat(PollRules.mayCancel(orga, ME, Status.UPCOMING)).isTrue();
        assertThat(PollRules.mayCancel(author, ME, Status.ENDED)).isFalse();
        assertThat(PollRules.mayCancel(author, ME, Status.CANCELLED)).isFalse();
        assertThat(PollRules.mayCancel(new PollRules.Actor(OTHER, false, true, true, false, false, false), ME,
                Status.RUNNING)).isFalse();
        assertThat(PollRules.mayVote(author, Status.RUNNING, false, true)).isTrue();
        assertThat(PollRules.mayVote(author, Status.RUNNING, true, true)).isFalse();
        assertThat(PollRules.mayVote(author, Status.RUNNING, false, false)).isFalse();
        assertThat(PollRules.mayVote(author, Status.UPCOMING, false, true)).isFalse();
        assertThat(PollRules.maySeeResults(Status.RUNNING)).isFalse();
        assertThat(PollRules.maySeeResults(Status.ENDED)).isTrue();
        PollRules.Actor member = new PollRules.Actor(OTHER, false, true, true, false, false, false);
        assertThat(PollRules.maySeeEligibleCount(author, ME, Status.RUNNING)).isTrue();
        assertThat(PollRules.maySeeEligibleCount(member, ME, Status.RUNNING)).isFalse();
        assertThat(PollRules.maySeeEligibleCount(member, ME, Status.ENDED)).isTrue();
    }

    @Test
    void leadTimeAndOptions() {
        assertThat(PollRules.validDates(NOW.plusSeconds(3600), NOW.plusSeconds(7200), NOW)).isTrue();
        assertThat(PollRules.validDates(NOW.plusSeconds(3599), NOW.plusSeconds(7200), NOW)).isFalse();
        assertThat(PollRules.validDates(NOW.plusSeconds(7200), NOW.plusSeconds(7200), NOW)).isFalse();
        assertThat(PollRules.validOptions(List.of("A", "B"))).isTrue();
        assertThat(PollRules.validOptions(List.of("A"))).isFalse();
        assertThat(PollRules.validOptions(List.of("A", " a "))).isFalse();
        assertThat(PollRules.validOptions(List.of("A", " "))).isFalse();
        assertThat(PollRules.scopes(true)).containsExactly(PollScope.FOODSAVERS, PollScope.VERIFIED_FOODSAVERS);
        assertThat(PollRules.scopes(false)).hasSize(5);
    }

    @Test
    void votesPerType() {
        assertThat(PollRules.values(PollType.THUMBS)).containsExactly(1, 0, -1);
        assertThat(PollRules.values(PollType.SCORE)).containsExactly(3, 2, 1, 0, -1, -2, -3);
        assertThat(PollRules.validVote(PollType.SELECT_ONE, 3, Map.of(1, 1))).isTrue();
        assertThat(PollRules.validVote(PollType.SELECT_ONE, 3, Map.of(1, 1, 2, 1))).isFalse();
        assertThat(PollRules.validVote(PollType.SELECT_ONE, 3, Map.of(3, 1))).isFalse();
        assertThat(PollRules.validVote(PollType.SELECT_ONE, 3, Map.of(0, 2))).isFalse();
        assertThat(PollRules.validVote(PollType.SELECT_MULTIPLE, 3, Map.of(0, 1, 2, 1))).isTrue();
        assertThat(PollRules.validVote(PollType.SELECT_MULTIPLE, 3, Map.of(0, -1))).isFalse();
        assertThat(PollRules.validVote(PollType.THUMBS, 2, Map.of(0, 1, 1, -1))).isTrue();
        assertThat(PollRules.validVote(PollType.THUMBS, 2, Map.of(0, 1))).isFalse();
        assertThat(PollRules.validVote(PollType.THUMBS, 2, Map.of(0, 1, 1, 2))).isFalse();
        assertThat(PollRules.validVote(PollType.SCORE, 2, Map.of(0, 3, 1, -3))).isTrue();
        assertThat(PollRules.validVote(PollType.SCORE, 2, Map.of(0, 4, 1, -3))).isFalse();
        assertThat(PollRules.validVote(PollType.SCORE, 2, null)).isFalse();
    }
}
