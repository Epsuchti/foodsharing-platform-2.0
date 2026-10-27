package network.foodsharing.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.quiz.domain.AnswerRating;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.service.QuizRuleService;
import network.foodsharing.quiz.service.QuizRuleService.Attempt;
import network.foodsharing.quiz.service.QuizRuleService.Candidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The quiz rules of plan §13.9 Q2 (selection, scoring, waiting, expiry, who may try, timing). */
class QuizRuleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private final QuizRuleService rules = new QuizRuleService(new AppProperties());

    @Test
    void selectionTakesMandatoryFirstThenProportionallyPerFailurePoints() {
        List<Candidate> pool = new ArrayList<>();
        pool.add(new Candidate(UUID.randomUUID(), 3, true));
        pool.add(new Candidate(UUID.randomUUID(), 3, true));
        for (int i = 0; i < 10; i++) {
            pool.add(new Candidate(UUID.randomUUID(), 1, false));
        }
        for (int i = 0; i < 6; i++) {
            pool.add(new Candidate(UUID.randomUUID(), 2, false));
        }
        for (int seed = 0; seed < 20; seed++) {
            List<Candidate> selected = rules.select(pool, 10, new Random(seed));
            assertThat(selected).hasSize(10).doesNotHaveDuplicates();
            assertThat(selected).filteredOn(Candidate::mandatory).hasSize(2);
            Map<Integer, Long> byPoints = selected.stream().filter(c -> !c.mandatory())
                    .collect(Collectors.groupingBy(Candidate::failurePoints, Collectors.counting()));
            // 8 remaining: 10/16 × 8 = 5, 6/16 × 8 = 3
            assertThat(byPoints).containsEntry(1, 5L).containsEntry(2, 3L);
        }
        assertThat(rules.select(pool, 50, new Random(1))).hasSize(pool.size());
        assertThat(rules.select(pool.subList(0, 2), 1, new Random(1))).hasSize(1);
        assertThat(rules.select(List.of(), 5, new Random(1))).isEmpty();
    }

    @Test
    void failurePointsCountMistakesAmongValuedAnswers() {
        UUID correct = UUID.randomUUID();
        UUID correct2 = UUID.randomUUID();
        UUID wrong = UUID.randomUUID();
        UUID neutral = UUID.randomUUID();
        Map<UUID, AnswerRating> answers = new LinkedHashMap<>();
        answers.put(correct, AnswerRating.CORRECT);
        answers.put(correct2, AnswerRating.CORRECT);
        answers.put(wrong, AnswerRating.WRONG);
        answers.put(neutral, AnswerRating.NEUTRAL);

        assertThat(rules.failurePoints(3, answers, List.of(correct, correct2), false)).isEqualByComparingTo("0");
        assertThat(rules.failurePoints(3, answers, List.of(correct, correct2, neutral), false)).isEqualByComparingTo("0");
        assertThat(rules.failurePoints(3, answers, List.of(correct), false)).isEqualByComparingTo("1.00");
        assertThat(rules.failurePoints(2, answers, List.of(correct, wrong), false)).isEqualByComparingTo("1.33");
        assertThat(rules.failurePoints(3, answers, List.of(wrong), false)).isEqualByComparingTo("3.00");
        assertThat(rules.failurePoints(3, answers, List.of(), true)).isEqualByComparingTo("3.00");
        assertThat(rules.failurePoints(2, Map.of(neutral, AnswerRating.NEUTRAL), List.of(), false))
                .isEqualByComparingTo("0");
        assertThat(rules.passed(new BigDecimal("3.00"), 3)).isTrue();
        assertThat(rules.passed(new BigDecimal("3.01"), 3)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "FOODSAVER, 1, 0", "FOODSAVER, 2, 0", "FOODSAVER, 3, 30", "FOODSAVER, 4, -1", "FOODSAVER, 5, -1",
            "STORE_MANAGER, 3, 30", "AMBASSADOR, 4, -1",
            "HYGIENE, 1, 1", "HYGIENE, 2, 1", "HYGIENE, 6, 1",
            "FOODSHARER, 3, 0", "SAVING_FOOD, 5, 0", "REPORTS, 4, 0"})
    void waitTable(QuizKind kind, int failures, int waitDays) {
        assertThat(rules.waitDays(kind, failures)).isEqualTo(waitDays);
    }

    @Test
    void triesAreCountedSinceTheLastPassAndTheWaitRunsFromTheLastFailure() {
        List<Attempt> attempts = new ArrayList<>(List.of(failed(400), passed(300), failed(20), failed(15)));
        var state = rules.tryState(QuizKind.FOODSAVER, attempts, NOW);
        assertThat(state.triesSinceLastPass()).isEqualTo(2);
        assertThat(state.currentWaitDays()).isZero();
        assertThat(state.waitDaysAfterNextFailure()).isEqualTo(30);

        attempts = List.of(failed(20), failed(15), failed(10));
        state = rules.tryState(QuizKind.FOODSAVER, attempts, NOW);
        assertThat(state.currentWaitDays()).isEqualTo(20);
        assertThat(state.waitDaysAfterNextFailure()).isEqualTo(-1);
        assertThat(rules.mayStart(QuizKind.FOODSAVER, state)).isFalse();
        assertThat(rules.tryState(QuizKind.FOODSAVER, List.of(failed(90), failed(80), failed(31)), NOW)
                .currentWaitDays()).isZero();

        state = rules.tryState(QuizKind.FOODSAVER, List.of(failed(90), failed(80), failed(70), failed(10)), NOW);
        assertThat(state.disqualified()).isTrue();
        assertThat(rules.mayStart(QuizKind.FOODSAVER, state)).isFalse();

        // running sessions never count
        state = rules.tryState(QuizKind.FOODSAVER, List.of(new Attempt(QuizSessionStatus.RUNNING, null)), NOW);
        assertThat(state.triesSinceLastPass()).isZero();
        assertThat(rules.mayStart(QuizKind.FOODSAVER, state)).isTrue();
    }

    @Test
    void passedQuizzesCannotBeRepeatedExceptHygiene() {
        var passed = rules.tryState(QuizKind.FOODSAVER, List.of(passed(10)), NOW);
        assertThat(passed.passValid()).isTrue();
        assertThat(rules.mayStart(QuizKind.FOODSAVER, passed)).isFalse();
        var hygiene = rules.tryState(QuizKind.HYGIENE, List.of(passed(10)), NOW);
        assertThat(rules.mayStart(QuizKind.HYGIENE, hygiene)).isTrue();
        assertThat(rules.tryState(QuizKind.HYGIENE, List.of(failed(0)), NOW.plusSeconds(3600)).currentWaitDays())
                .isEqualTo(1);
    }

    @Test
    void hygieneExpiresAfterAYearWithAWarningWindowOf90Days() {
        assertThat(rules.tryState(QuizKind.HYGIENE, List.of(passed(100)), NOW).expiresInDays()).isNull();
        var warning = rules.tryState(QuizKind.HYGIENE, List.of(passed(300)), NOW);
        assertThat(warning.expiresInDays()).isEqualTo(65);
        assertThat(warning.passValid()).isTrue();
        var expired = rules.tryState(QuizKind.HYGIENE, List.of(passed(366)), NOW);
        assertThat(expired.passValid()).isFalse();
        assertThat(expired.expiresInDays()).isNull();
        assertThat(rules.tryState(QuizKind.FOODSAVER, List.of(passed(2000)), NOW).passValid()).isTrue();
    }

    @Test
    void whoMayTry() {
        for (QuizKind open : List.of(QuizKind.FOODSAVER, QuizKind.HYGIENE, QuizKind.FOODSHARER, QuizKind.SAVING_FOOD)) {
            assertThat(rules.mayTry(open, Role.FOODSHARER, false, false)).as(open.name()).isTrue();
        }
        assertThat(rules.mayTry(QuizKind.STORE_MANAGER, Role.FOODSHARER, true, false)).isFalse();
        assertThat(rules.mayTry(QuizKind.STORE_MANAGER, Role.FOODSAVER, false, false)).isFalse();
        assertThat(rules.mayTry(QuizKind.STORE_MANAGER, Role.FOODSAVER, true, false)).isTrue();
        assertThat(rules.mayTry(QuizKind.AMBASSADOR, Role.FOODSAVER, true, false)).isFalse();
        assertThat(rules.mayTry(QuizKind.AMBASSADOR, Role.STORE_MANAGER, true, false)).isTrue();
        assertThat(rules.mayTry(QuizKind.REPORTS, Role.AMBASSADOR, true, false)).isFalse();
        assertThat(rules.mayTry(QuizKind.REPORTS, Role.FOODSAVER, true, true)).isTrue();
        assertThat(rules.mayTry(QuizKind.REPORTS, Role.ORGA, true, false)).isTrue();
        assertThat(rules.requiresConfirmation(QuizKind.FOODSAVER)).isTrue();
        assertThat(rules.requiresConfirmation(QuizKind.AMBASSADOR)).isFalse();
        assertThat(rules.roleOf(QuizKind.STORE_MANAGER)).isEqualTo(Role.STORE_MANAGER);
        assertThat(rules.roleOf(QuizKind.HYGIENE)).isNull();
    }

    @Test
    void timedAnswersGetANetworkBuffer() {
        assertThat(rules.timedOut(true, Duration.ofSeconds(64), 60)).isFalse();
        assertThat(rules.timedOut(true, Duration.ofSeconds(65), 60)).isTrue();
        assertThat(rules.timedOut(false, Duration.ofHours(5), 60)).isFalse();
    }

    private static Attempt failed(int daysAgo) {
        return new Attempt(QuizSessionStatus.FAILED, NOW.minus(Duration.ofDays(daysAgo)));
    }

    private static Attempt passed(int daysAgo) {
        return new Attempt(QuizSessionStatus.PASSED, NOW.minus(Duration.ofDays(daysAgo)));
    }
}
