package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.intropickup.service.IntroPickupProgressService.Summary;
import network.foodsharing.intropickup.service.NewcomerProgressService.NextStep;
import network.foodsharing.intropickup.service.NewcomerProgressService.Stage;
import network.foodsharing.onboarding.domain.JourneyStepStatus;
import network.foodsharing.onboarding.service.JourneyCalculator.ExtraStep;
import network.foodsharing.onboarding.service.JourneyCalculator.Inputs;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.onboarding.service.JourneyCalculator.StepView;
import network.foodsharing.onboarding.service.JourneyCalculator.TrackInputs;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.service.QuizRuleService.TryState;
import network.foodsharing.region.domain.Region;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** Every transition of the journey state machine (plan §11.1). */
class JourneyCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final List<String> CHAPTERS = List.of("rules", "hygiene", "how-a-pickup-works", "etiquette");
    private static final User CONTACT = contact();
    private static final TrackInputs NO_TRACKS = new TrackInputs(null, null, null, false);

    private static User contact() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName("Ambassador");
        return user;
    }

    /** Builder over the inputs, starting with a fresh account. */
    private static final class Case {
        User user = new User();
        boolean phone;
        Region region;
        boolean mandatory = true;
        Set<String> read = Set.of();
        TryState quiz = new TryState(null, 0, 0, 0, null, null, false);
        boolean agreement;
        Summary einabs;
        List<ExtraStep> extras = List.of();
        TrackInputs tracks = NO_TRACKS;

        Case() {
            user.setId(UUID.randomUUID());
            user.setRole(Role.FOODSHARER);
        }

        Case profile() {
            user.setFirstName("Ana");
            user.setLastName("Muster");
            user.setMobilePhone("+41791234567");
            phone = true;
            region = new Region();
            region.setId(UUID.randomUUID());
            user.setHomeRegionId(region.getId());
            einabs = summary(Stage.REGISTERED, NextStep.BOOK_EINAB, 0, 0);
            return this;
        }

        Case learned() {
            read = Set.copyOf(CHAPTERS);
            return this;
        }

        Case quizPassed() {
            user.setQuizRole(Role.FOODSAVER);
            return this;
        }

        Case agreed() {
            agreement = true;
            return this;
        }

        Case einabs(Stage stage, NextStep next, int counted, int upcoming) {
            einabs = summary(stage, next, counted, upcoming);
            return this;
        }

        Case verified() {
            user.setVerified(true);
            user.setVerifiedAt(NOW);
            return this;
        }

        JourneyView compute() {
            return JourneyCalculator.compute(new Inputs(user, phone, region, mandatory, CHAPTERS, read, quiz,
                    List.of(CONTACT), agreement, einabs, extras, List.of(CONTACT), 12, tracks));
        }
    }

    private static Summary summary(Stage stage, NextStep next, int counted, int upcoming) {
        return new Summary(stage, next, counted, upcoming, 3, true, counted >= 3, false, false, NOW);
    }

    private static StepView step(JourneyView journey, String key) {
        return journey.steps().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    private static void expectCurrent(JourneyView journey, String key, JourneyStepStatus status) {
        assertThat(journey.currentStep()).isEqualTo(key);
        assertThat(journey.complete()).isFalse();
        assertThat(step(journey, key).status()).isEqualTo(status);
        boolean after = false;
        for (StepView s : journey.steps()) {
            if (after && s.status() != JourneyStepStatus.DONE) {
                assertThat(s.status()).as(s.key()).isEqualTo(JourneyStepStatus.LOCKED);
                assertThat(s.nextAction()).isNull();
            }
            after |= s.key().equals(key);
        }
    }

    @Test
    void freshAccountStartsWithTheProfile() {
        JourneyView journey = new Case().compute();
        assertThat(step(journey, "ACCOUNT").status()).isEqualTo(JourneyStepStatus.DONE);
        expectCurrent(journey, "PROFILE_REGION", JourneyStepStatus.AVAILABLE);
        assertThat(step(journey, "PROFILE_REGION").nextAction().route()).isEqualTo("/onboarding/profile");
        assertThat((List<?>) step(journey, "PROFILE_REGION").details().get("missing")).hasSize(4);
    }

    @Test
    void partialProfileIsInProgress() {
        Case c = new Case();
        c.user.setFirstName("Ana");
        expectCurrent(c.compute(), "PROFILE_REGION", JourneyStepStatus.IN_PROGRESS);
    }

    @Test
    void learningPagesThenOptionalWhenNotMandatory() {
        JourneyView journey = new Case().profile().compute();
        expectCurrent(journey, "LEARN", JourneyStepStatus.AVAILABLE);
        assertThat(step(journey, "LEARN").nextAction().route()).isEqualTo("/learn/rules");
        Case some = new Case().profile();
        some.read = Set.of("rules");
        JourneyView partial = some.compute();
        expectCurrent(partial, "LEARN", JourneyStepStatus.IN_PROGRESS);
        assertThat(step(partial, "LEARN").progress().done()).isEqualTo(1);
        assertThat(step(partial, "LEARN").nextAction().route()).isEqualTo("/learn/hygiene");
        assertThat(step(partial, "LEARN").details()).containsEntry("readChapters", java.util.List.of("rules"));
        assertThat(step(journey, "LEARN").details()).containsEntry("readChapters", java.util.List.of());
        Case optional = new Case().profile();
        optional.mandatory = false;
        JourneyView skipped = optional.compute();
        assertThat(step(skipped, "LEARN").status()).isEqualTo(JourneyStepStatus.DONE);
        assertThat(step(skipped, "LEARN").optional()).isTrue();
        assertThat(skipped.currentStep()).isEqualTo("QUIZ");
    }

    @Test
    void quizStates() {
        expectCurrent(new Case().profile().learned().compute(), "QUIZ", JourneyStepStatus.AVAILABLE);
        Case waiting = new Case().profile().learned();
        waiting.quiz = new TryState(QuizSessionStatus.FAILED, 3, 30, 30, null, null, false);
        JourneyView wait = waiting.compute();
        expectCurrent(wait, "QUIZ", JourneyStepStatus.IN_PROGRESS);
        assertThat(step(wait, "QUIZ").blockedReason()).isEqualTo("QUIZ_WAIT_TIME");
        assertThat(step(wait, "QUIZ").blockedArgs()).containsEntry("days", "30");
        Case disqualified = new Case().profile().learned();
        disqualified.quiz = new TryState(QuizSessionStatus.FAILED, 4, -1, -1, null, null, false);
        JourneyView blocked = disqualified.compute();
        expectCurrent(blocked, "QUIZ", JourneyStepStatus.WAITING_FOR_OTHERS);
        assertThat(step(blocked, "QUIZ").waitingFor().contacts()).containsExactly(CONTACT);
        Case passed = new Case().profile().learned();
        passed.quiz = new TryState(QuizSessionStatus.PASSED, 0, 0, 0, NOW, null, true);
        JourneyView confirm = passed.compute();
        expectCurrent(confirm, "QUIZ", JourneyStepStatus.IN_PROGRESS);
        assertThat(step(confirm, "QUIZ").nextAction().route()).isEqualTo("/quizzes/FOODSAVER/results");
        Case failedOnce = new Case().profile().learned();
        failedOnce.quiz = new TryState(QuizSessionStatus.FAILED, 1, 0, 0, null, null, false);
        expectCurrent(failedOnce.compute(), "QUIZ", JourneyStepStatus.IN_PROGRESS);
    }

    @Test
    void agreementAfterTheQuiz() {
        JourneyView journey = new Case().profile().learned().quizPassed().compute();
        expectCurrent(journey, "AGREEMENT", JourneyStepStatus.AVAILABLE);
        assertThat(step(journey, "AGREEMENT").nextAction().route()).isEqualTo("/legal/LEGAL_AGREEMENT");
    }

    @Test
    void introPickupStates() {
        Case base = new Case().profile().learned().quizPassed().agreed();
        JourneyView none = base.compute();
        expectCurrent(none, "INTRO_PICKUPS", JourneyStepStatus.AVAILABLE);
        assertThat(step(none, "INTRO_PICKUPS").progress().required()).isEqualTo(3);
        JourneyView some = base.einabs(Stage.IN_PROGRESS, NextStep.BOOK_FAIRTEILER_EINAB, 1, 1).compute();
        expectCurrent(some, "INTRO_PICKUPS", JourneyStepStatus.IN_PROGRESS);
        assertThat(step(some, "INTRO_PICKUPS").blockedReason()).isEqualTo("FAIRTEILER_VISIT_REQUIRED");
        assertThat(step(some, "INTRO_PICKUPS").progress().booked()).isEqualTo(1);
        JourneyView allBooked = base.einabs(Stage.IN_PROGRESS, NextStep.ATTEND_BOOKED, 1, 2).compute();
        expectCurrent(allBooked, "INTRO_PICKUPS", JourneyStepStatus.WAITING_FOR_OTHERS);
        assertThat(step(allBooked, "INTRO_PICKUPS").nextAction().route()).isEqualTo("/me/intro-pickups");
    }

    @Test
    void regionExtraStepsComeAfterTheIntroPickups() {
        Case c = new Case().profile().learned().quizPassed().agreed()
                .einabs(Stage.COMPLETE, NextStep.WAIT_FOR_VERIFICATION, 3, 0);
        c.extras = List.of(new ExtraStep(UUID.randomUUID(), "newcomer-meeting", "Neulingstreffen", false));
        JourneyView journey = c.compute();
        assertThat(journey.currentStep()).isEqualTo("newcomer-meeting");
        assertThat(journey.extraSteps().getFirst().status()).isEqualTo(JourneyStepStatus.WAITING_FOR_OTHERS);
        assertThat(journey.extraSteps().getFirst().title()).isEqualTo("Neulingstreffen");
        assertThat(step(journey, "ID_CHECK").status()).isEqualTo(JourneyStepStatus.LOCKED);
        c.extras = List.of(new ExtraStep(UUID.randomUUID(), "newcomer-meeting", "Neulingstreffen", true));
        assertThat(c.compute().currentStep()).isEqualTo("ID_CHECK");
    }

    @Test
    void idCheckWaitsForTheRegionThenThePassport() {
        Case c = new Case().profile().learned().quizPassed().agreed()
                .einabs(Stage.COMPLETE, NextStep.WAIT_FOR_VERIFICATION, 3, 0);
        JourneyView waiting = c.compute();
        expectCurrent(waiting, "ID_CHECK", JourneyStepStatus.WAITING_FOR_OTHERS);
        assertThat(step(waiting, "ID_CHECK").waitingFor().typicalDays()).isEqualTo(12);
        assertThat(step(waiting, "ID_CHECK").waitingFor().contacts()).containsExactly(CONTACT);
        assertThat(step(waiting, "INTRO_PICKUPS").status()).isEqualTo(JourneyStepStatus.DONE);

        JourneyView passport = c.verified().compute();
        expectCurrent(passport, "PASSPORT", JourneyStepStatus.AVAILABLE);
        assertThat(step(passport, "PASSPORT").nextAction().route()).isEqualTo("/me/passport");

        c.user.setLastPassportAt(NOW);
        JourneyView done = c.compute();
        assertThat(done.complete()).isTrue();
        assertThat(done.currentStep()).isNull();
        assertThat(done.steps()).allMatch(s -> s.status() == JourneyStepStatus.DONE);
    }

    @Test
    void doneStepsStayDoneAfterTheCurrentStep() {
        Case c = new Case().profile().agreed();
        JourneyView journey = c.compute();
        expectCurrent(journey, "LEARN", JourneyStepStatus.AVAILABLE);
        assertThat(step(journey, "AGREEMENT").status()).isEqualTo(JourneyStepStatus.DONE);
    }

    @Test
    void verifiedUsersSkipOpenRegionStepsAndTracksOpenUp() {
        Case c = new Case().profile().learned().quizPassed().agreed().verified();
        c.extras = List.of(new ExtraStep(UUID.randomUUID(), "newcomer-meeting", "Treffen", false));
        c.einabs(Stage.VERIFIED, NextStep.DONE, 3, 0);
        c.tracks = new TrackInputs(new TryState(QuizSessionStatus.PASSED, 0, 0, 0, NOW, 300, true), null, null,
                false);
        JourneyView journey = c.compute();
        assertThat(journey.extraSteps().getFirst().status()).isEqualTo(JourneyStepStatus.DONE);
        assertThat(journey.currentStep()).isEqualTo("PASSPORT");
        assertThat(journey.tracks()).extracting(JourneyCalculator.Track::key)
                .containsExactly("HYGIENE", "STORE_MANAGER", "GUIDE", "AMBASSADOR");
        assertThat(journey.tracks().get(0).steps().getFirst().status()).isEqualTo(JourneyStepStatus.DONE);
        assertThat(journey.tracks().get(1).steps().getFirst().status()).isEqualTo(JourneyStepStatus.AVAILABLE);
        assertThat(journey.tracks().get(2).steps().getFirst().status())
                .isEqualTo(JourneyStepStatus.WAITING_FOR_OTHERS);
        assertThat(journey.tracks().get(3).steps().getFirst().status()).isEqualTo(JourneyStepStatus.LOCKED);
        JourneyView newcomer = new Case().compute();
        assertThat(newcomer.tracks().get(1).steps().getFirst().status()).isEqualTo(JourneyStepStatus.LOCKED);
        assertThat(newcomer.tracks().get(0).steps().getFirst().status()).isEqualTo(JourneyStepStatus.AVAILABLE);
    }
}
