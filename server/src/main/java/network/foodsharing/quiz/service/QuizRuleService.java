package network.foodsharing.quiz.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.quiz.domain.AnswerRating;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.security.PermissionService;
import org.springframework.stereotype.Service;

/**
 * The quiz rules (plan §13.9 Q2, ported from upstream {@code QuizTransactions} / {@code QuizPermissions}); pure
 * functions, all times passed in.
 * <ul>
 *   <li>Question selection: all mandatory questions first, then the rest proportionally per failure-point bucket
 *       (largest-remainder rounding), random within a bucket; questions and answers are shuffled.</li>
 *   <li>Failure points of a question: timed out → its full failure points; otherwise
 *       {@code round(mistakes × fp / valued answers, 2)}, valued = non-NEUTRAL answers, mistake = an answer selected
 *       but not CORRECT or CORRECT but not selected. Passed when the sum ≤ the quiz's maximum.</li>
 *   <li>Waiting (failed tries since the last pass; running and test sessions never count): role quizzes 0 days after
 *       1–2 failures, 30 days after the 3rd, disqualified after the 4th; hygiene 1 day after every failure; other
 *       quizzes never wait.</li>
 *   <li>Expiry: hygiene passes are valid {@code app.quiz.hygiene-validity-days}; {@code expiresInDays} is only set
 *       inside the warning window. Confirmation: FOODSAVER and STORE_MANAGER passes must be confirmed.</li>
 *   <li>Who may try: FOODSAVER, HYGIENE, FOODSHARER, SAVING_FOOD everyone; STORE_MANAGER verified FOODSAVERs or
 *       above, AMBASSADOR verified STORE_MANAGERs or above; REPORTS members of a group editing the REPORTS quiz.</li>
 *   <li>Timing: a timed answer counts when given within duration + {@code app.quiz.network-buffer-seconds}.</li>
 * </ul>
 */
@Service
public class QuizRuleService {

    public static final Set<QuizKind> ROLE_QUIZZES = Set.of(QuizKind.FOODSAVER, QuizKind.STORE_MANAGER,
            QuizKind.AMBASSADOR);

    /** A selectable question. */
    public record Candidate(UUID id, int failurePoints, boolean mandatory) {
    }

    /** A finished (non-test) session for the try rules, any order. */
    public record Attempt(QuizSessionStatus status, Instant endedAt) {
    }

    public record TryState(QuizSessionStatus lastStatus, int triesSinceLastPass, int currentWaitDays,
                           int waitDaysAfterNextFailure, Instant passedAt, Integer expiresInDays, boolean passValid) {
        public boolean disqualified() {
            return currentWaitDays < 0;
        }
    }

    private final AppProperties.Quiz settings;

    public QuizRuleService(AppProperties properties) {
        this.settings = properties.getQuiz();
    }

    // ------------------------------------------------------------------------------------------ selection

    public List<Candidate> select(List<Candidate> pool, int count, Random random) {
        List<Candidate> mandatory = new ArrayList<>(pool.stream().filter(Candidate::mandatory).toList());
        Collections.shuffle(mandatory, random);
        List<Candidate> result = new ArrayList<>(mandatory.subList(0, Math.min(count, mandatory.size())));
        int remaining = Math.min(count, pool.size()) - result.size();
        if (remaining > 0) {
            Map<Integer, List<Candidate>> buckets = new TreeMap<>(pool.stream().filter(c -> !c.mandatory())
                    .collect(Collectors.groupingBy(Candidate::failurePoints)));
            int total = buckets.values().stream().mapToInt(List::size).sum();
            Map<Integer, Integer> take = new TreeMap<>();
            List<double[]> remainders = new ArrayList<>();
            int assigned = 0;
            for (Map.Entry<Integer, List<Candidate>> bucket : buckets.entrySet()) {
                double exact = (double) remaining * bucket.getValue().size() / total;
                int floor = (int) Math.floor(exact);
                take.put(bucket.getKey(), floor);
                assigned += floor;
                remainders.add(new double[] {bucket.getKey(), exact - floor});
            }
            remainders.sort(Comparator.comparingDouble((double[] r) -> -r[1]).thenComparingDouble(r -> r[0]));
            for (int i = 0; assigned < remaining && i < remainders.size(); i++) {
                int key = (int) remainders.get(i)[0];
                if (take.get(key) < buckets.get(key).size()) {
                    take.merge(key, 1, Integer::sum);
                    assigned++;
                }
            }
            for (Map.Entry<Integer, List<Candidate>> bucket : buckets.entrySet()) {
                List<Candidate> shuffled = new ArrayList<>(bucket.getValue());
                Collections.shuffle(shuffled, random);
                result.addAll(shuffled.subList(0, Math.min(take.get(bucket.getKey()), shuffled.size())));
            }
        }
        Collections.shuffle(result, random);
        return result;
    }

    // ------------------------------------------------------------------------------------------ scoring

    /** Failure points of one answered question (rounded to 2 decimals). */
    public BigDecimal failurePoints(int questionFailurePoints, Map<UUID, AnswerRating> answers,
                                    Collection<UUID> selected, boolean timedOut) {
        if (timedOut) {
            return BigDecimal.valueOf(questionFailurePoints).setScale(2, RoundingMode.HALF_UP);
        }
        Set<UUID> chosen = selected == null ? Set.of() : Set.copyOf(selected);
        long valued = answers.values().stream().filter(rating -> rating != AnswerRating.NEUTRAL).count();
        if (valued == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long mistakes = answers.entrySet().stream().filter(entry -> entry.getValue() != AnswerRating.NEUTRAL)
                .filter(entry -> chosen.contains(entry.getKey()) != (entry.getValue() == AnswerRating.CORRECT))
                .count();
        return BigDecimal.valueOf(mistakes * questionFailurePoints)
                .divide(BigDecimal.valueOf(valued), 2, RoundingMode.HALF_UP);
    }

    public boolean passed(BigDecimal total, int maxFailurePoints) {
        return total.compareTo(BigDecimal.valueOf(maxFailurePoints)) <= 0;
    }

    /** A timed answer is accepted until duration + network buffer. */
    public boolean timedOut(boolean timed, Duration questionAge, int durationSeconds) {
        return timed && durationSeconds > 0
                && questionAge.compareTo(Duration.ofSeconds(durationSeconds + settings.getNetworkBufferSeconds())) >= 0;
    }

    // ------------------------------------------------------------------------------------------ tries

    /** Days to wait after {@code failedTries} failures since the last pass; -1 = disqualified. */
    public int waitDays(QuizKind kind, int failedTries) {
        if (failedTries <= 0) {
            return 0;
        }
        if (ROLE_QUIZZES.contains(kind)) {
            if (failedTries >= 4) {
                return -1;
            }
            return failedTries == 3 ? settings.getRoleQuizWaitDaysThirdTry() : 0;
        }
        return kind == QuizKind.HYGIENE ? 1 : 0;
    }

    public TryState tryState(QuizKind kind, List<Attempt> attempts, Instant now) {
        List<Attempt> finished = attempts.stream().filter(a -> a.endedAt() != null && a.status() != null
                        && a.status() != QuizSessionStatus.RUNNING)
                .sorted(Comparator.comparing(Attempt::endedAt)).toList();
        Attempt lastPass = null;
        int failedSincePass = 0;
        for (Attempt attempt : finished) {
            if (attempt.status() == QuizSessionStatus.PASSED) {
                lastPass = attempt;
                failedSincePass = 0;
            } else {
                failedSincePass++;
            }
        }
        QuizSessionStatus lastStatus = finished.isEmpty() ? null : finished.getLast().status();
        Integer expiresInDays = null;
        boolean passValid = lastPass != null;
        if (lastPass != null && kind == QuizKind.HYGIENE) {
            Instant expiry = lastPass.endedAt().plus(Duration.ofDays(settings.getHygieneValidityDays()));
            long days = ceilDays(Duration.between(now, expiry));
            passValid = expiry.isAfter(now);
            if (passValid && days <= settings.getHygieneWarningDays()) {
                expiresInDays = (int) days;
            }
        }
        int wait = waitDays(kind, failedSincePass);
        int currentWait = 0;
        if (wait < 0) {
            currentWait = -1;
        } else if (wait > 0 && lastStatus == QuizSessionStatus.FAILED) {
            Instant until = finished.getLast().endedAt().plus(Duration.ofDays(wait));
            currentWait = until.isAfter(now) ? (int) ceilDays(Duration.between(now, until)) : 0;
        }
        return new TryState(lastStatus, failedSincePass, currentWait, waitDays(kind, failedSincePass + 1),
                lastPass == null ? null : lastPass.endedAt(), expiresInDays, passValid);
    }

    /** May the user start a session now (no running session checked here)? */
    public boolean mayStart(QuizKind kind, TryState state) {
        if (state.currentWaitDays() != 0) {
            return false;
        }
        return !state.passValid() || kind == QuizKind.HYGIENE;
    }

    // ------------------------------------------------------------------------------------------ roles

    public boolean mayTry(QuizKind kind, Role role, boolean verified, boolean editorGroupMember) {
        return switch (kind) {
            case FOODSAVER, HYGIENE, FOODSHARER, SAVING_FOOD -> true;
            case STORE_MANAGER -> verified && PermissionService.isAtLeast(role, Role.FOODSAVER);
            case AMBASSADOR -> verified && PermissionService.isAtLeast(role, Role.STORE_MANAGER);
            case REPORTS -> editorGroupMember || role == Role.ORGA;
        };
    }

    public boolean requiresConfirmation(QuizKind kind) {
        return kind == QuizKind.FOODSAVER || kind == QuizKind.STORE_MANAGER;
    }

    /** The global role a role quiz leads to (null for the other quizzes). */
    public Role roleOf(QuizKind kind) {
        return switch (kind) {
            case FOODSAVER -> Role.FOODSAVER;
            case STORE_MANAGER -> Role.STORE_MANAGER;
            case AMBASSADOR -> Role.AMBASSADOR;
            default -> null;
        };
    }

    private static long ceilDays(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds <= 0) {
            return 0;
        }
        return (seconds + 86_399) / 86_400;
    }
}
