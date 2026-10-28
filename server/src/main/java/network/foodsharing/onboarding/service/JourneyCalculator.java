package network.foodsharing.onboarding.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.intropickup.service.NewcomerProgressService.NextStep;
import network.foodsharing.intropickup.service.NewcomerProgressService.Stage;
import network.foodsharing.onboarding.domain.JourneyStepKey;
import network.foodsharing.onboarding.domain.JourneyStepStatus;
import network.foodsharing.quiz.service.QuizRuleService.TryState;
import network.foodsharing.region.domain.Region;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;

/**
 * The journey state machine (plan §11.1), a pure function of the collected inputs. Order: ACCOUNT, PROFILE_REGION,
 * LEARN, QUIZ, AGREEMENT, INTRO_PICKUPS, region extra steps, ID_CHECK, PASSPORT. Every step is either DONE or open;
 * the first open step is the current one and gets its active status (AVAILABLE, IN_PROGRESS or WAITING_FOR_OTHERS,
 * with next action, blocked reason or who it waits for); later open steps are LOCKED. Complete = all DONE.
 */
public final class JourneyCalculator {

    public record Progress(int done, int booked, int required, Boolean fairteilerDone) {
    }

    public record Action(String labelKey, String route) {
    }

    public record WaitingFor(String labelKey, List<User> contacts, Integer typicalDays) {
    }

    public record StepView(String key, JourneyStepStatus status, String titleKey, String title, Progress progress,
                           Action nextAction, String estimatedDuration, String blockedReason,
                           Map<String, String> blockedArgs, WaitingFor waitingFor, boolean optional,
                           Map<String, Object> details) {
    }

    public record Track(String key, List<StepView> steps) {
    }

    public record JourneyView(boolean complete, String currentStep, List<StepView> steps, Region region,
                              List<StepView> extraSteps, List<Track> tracks) {
    }

    public record ExtraStep(UUID id, String key, String title, boolean done) {
    }

    public record TrackInputs(TryState hygiene, TryState storeManager, TryState ambassador, boolean guide) {
    }

    public record Inputs(User user, boolean phoneValid, Region region, boolean learningMandatory,
                         List<String> chapterKeys, Set<String> readChapters, TryState foodsaverQuiz,
                         List<User> quizContacts, boolean agreementAccepted,
                         IntroPickupProgressService.Summary introPickups, List<ExtraStep> extraSteps,
                         List<User> verificationContacts, Integer typicalVerificationDays, TrackInputs tracks) {
    }

    /** A step before ordering: done, or the state it has when it is the current step. */
    private record Draft(String key, boolean done, JourneyStepStatus activeStatus, String titleKey, String title,
                         Progress progress, Action action, String duration, String blockedReason,
                         Map<String, String> blockedArgs, WaitingFor waitingFor, boolean optional,
                         Map<String, Object> details) {

        static Draft of(JourneyStepKey key, boolean done, JourneyStepStatus active, Action action, String duration) {
            return new Draft(key.name(), done, active, JourneyCalculator.titleKey(key.name()), null, null, action, duration, null, null,
                    null, false, null);
        }

        Draft progress(Progress value) {
            return new Draft(key, done, activeStatus, titleKey, title, value, action, duration, blockedReason,
                    blockedArgs, waitingFor, optional, details);
        }

        Draft blocked(String reason, Map<String, String> args) {
            return new Draft(key, done, activeStatus, titleKey, title, progress, action, duration, reason, args,
                    waitingFor, optional, details);
        }

        Draft waiting(WaitingFor value) {
            return new Draft(key, done, activeStatus, titleKey, title, progress, action, duration, blockedReason,
                    blockedArgs, value, optional, details);
        }

        Draft optional(boolean value) {
            return new Draft(key, done, activeStatus, titleKey, title, progress, action, duration, blockedReason,
                    blockedArgs, waitingFor, value, details);
        }

        Draft details(Map<String, Object> value) {
            return new Draft(key, done, activeStatus, titleKey, title, progress, action, duration, blockedReason,
                    blockedArgs, waitingFor, optional, value);
        }

        StepView view(JourneyStepStatus status) {
            boolean active = status != JourneyStepStatus.DONE && status != JourneyStepStatus.LOCKED;
            return new StepView(key, status, titleKey, title, progress, active ? action : null, duration,
                    active ? blockedReason : null, active ? blockedArgs : null, active ? waitingFor : null, optional,
                    details);
        }
    }

    private JourneyCalculator() {
    }

    static String titleKey(String key) {
        return "onboarding.step." + key + ".title";
    }

    public static JourneyView compute(Inputs in) {
        User user = in.user();
        List<Draft> main = new ArrayList<>();
        main.add(Draft.of(JourneyStepKey.ACCOUNT, true, JourneyStepStatus.DONE, null, "PT2M"));
        main.add(profile(in));
        main.add(learn(in));
        main.add(quiz(in));
        main.add(Draft.of(JourneyStepKey.AGREEMENT, in.agreementAccepted(), JourneyStepStatus.AVAILABLE,
                new Action("onboarding.action.acceptAgreement", "/legal/LEGAL_AGREEMENT"), "PT5M"));
        Draft introPickups = introPickups(in);
        main.add(introPickups);
        List<Draft> extras = new ArrayList<>();
        for (ExtraStep step : in.extraSteps()) {
            extras.add(new Draft(step.key(), step.done() || user.isVerified(), JourneyStepStatus.WAITING_FOR_OTHERS,
                    null, step.title(), null, null, "P7D", null, null,
                    new WaitingFor("onboarding.waiting.regionStep", in.verificationContacts(), null), false, null));
        }
        Draft idCheck = Draft.of(JourneyStepKey.ID_CHECK, user.isVerified(),
                introPickups.done() ? JourneyStepStatus.WAITING_FOR_OTHERS : JourneyStepStatus.AVAILABLE, null, "P7D")
                .waiting(introPickups.done() ? new WaitingFor("onboarding.waiting.verification",
                        in.verificationContacts(), in.typicalVerificationDays()) : null);
        Draft passport = Draft.of(JourneyStepKey.PASSPORT, user.getLastPassportAt() != null,
                JourneyStepStatus.AVAILABLE, new Action("onboarding.action.downloadPassport", "/me/passport"), "PT2M");

        List<Draft> ordered = new ArrayList<>(main);
        ordered.addAll(extras);
        ordered.add(idCheck);
        ordered.add(passport);
        String current = null;
        Map<Draft, JourneyStepStatus> statuses = new java.util.IdentityHashMap<>();
        for (Draft draft : ordered) {
            JourneyStepStatus status;
            if (draft.done()) {
                status = JourneyStepStatus.DONE;
            } else if (current == null) {
                current = draft.key();
                status = draft.activeStatus();
            } else {
                status = JourneyStepStatus.LOCKED;
            }
            statuses.put(draft, status);
        }
        List<StepView> steps = new ArrayList<>();
        for (Draft draft : main) {
            steps.add(draft.view(statuses.get(draft)));
        }
        steps.add(idCheck.view(statuses.get(idCheck)));
        steps.add(passport.view(statuses.get(passport)));
        List<StepView> extraViews = extras.stream()
                .map(d -> d.view(statuses.get(d))).toList();
        return new JourneyView(current == null, current, steps, in.region(), extraViews, tracks(in));
    }

    private static Draft profile(Inputs in) {
        User user = in.user();
        List<String> missing = new ArrayList<>();
        if (blank(user.getFirstName())) {
            missing.add("firstName");
        }
        if (blank(user.getLastName())) {
            missing.add("lastName");
        }
        if (!in.phoneValid()) {
            missing.add("mobilePhone");
        }
        if (user.getHomeRegionId() == null) {
            missing.add("homeRegion");
        }
        boolean started = missing.size() < 4;
        return Draft.of(JourneyStepKey.PROFILE_REGION, missing.isEmpty(),
                        started ? JourneyStepStatus.IN_PROGRESS : JourneyStepStatus.AVAILABLE,
                        new Action("onboarding.action.completeProfile", "/onboarding/profile"), "PT5M")
                .details(Map.of("missing", List.copyOf(missing)));
    }

    private static Draft learn(Inputs in) {
        List<String> chapters = in.chapterKeys();
        int read = (int) chapters.stream().filter(in.readChapters()::contains).count();
        boolean allRead = read == chapters.size();
        String firstUnread = chapters.stream().filter(k -> !in.readChapters().contains(k)).findFirst().orElse(null);
        Draft draft = Draft.of(JourneyStepKey.LEARN, allRead || !in.learningMandatory(),
                        read > 0 ? JourneyStepStatus.IN_PROGRESS : JourneyStepStatus.AVAILABLE,
                        new Action("onboarding.action.readChapter", firstUnread == null ? "/learn"
                                : "/learn/" + firstUnread), "PT20M")
                .progress(new Progress(read, 0, chapters.size(), null))
                .details(Map.of("readChapters", chapters.stream().filter(in.readChapters()::contains).toList()));
        return draft.optional(!in.learningMandatory());
    }

    private static Draft quiz(Inputs in) {
        User user = in.user();
        boolean done = PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)
                || (user.getQuizRole() != null && PermissionService.isAtLeast(user.getQuizRole(), Role.FOODSAVER));
        TryState state = in.foodsaverQuiz();
        Action start = new Action("onboarding.action.startQuiz", "/quizzes/FOODSAVER");
        if (done || state == null) {
            return Draft.of(JourneyStepKey.QUIZ, done, JourneyStepStatus.AVAILABLE, start, "PT30M");
        }
        if (state.disqualified()) {
            return Draft.of(JourneyStepKey.QUIZ, false, JourneyStepStatus.WAITING_FOR_OTHERS, null, "PT30M")
                    .blocked("QUIZ_DISQUALIFIED", Map.of())
                    .waiting(new WaitingFor("onboarding.waiting.quizTeam", in.quizContacts(), null));
        }
        if (state.passValid()) {
            return Draft.of(JourneyStepKey.QUIZ, false, JourneyStepStatus.IN_PROGRESS,
                    new Action("onboarding.action.confirmQuiz", "/quizzes/FOODSAVER/results"), "PT30M");
        }
        if (state.currentWaitDays() > 0) {
            return Draft.of(JourneyStepKey.QUIZ, false, JourneyStepStatus.IN_PROGRESS, start, "PT30M")
                    .blocked("QUIZ_WAIT_TIME", Map.of("days", String.valueOf(state.currentWaitDays())));
        }
        return Draft.of(JourneyStepKey.QUIZ, false, state.lastStatus() == null ? JourneyStepStatus.AVAILABLE
                : JourneyStepStatus.IN_PROGRESS, start, "PT30M");
    }

    private static Draft introPickups(Inputs in) {
        IntroPickupProgressService.Summary s = in.introPickups();
        Action book = new Action("onboarding.action.bookIntroPickup", "/intro-pickups");
        if (s == null) {
            return Draft.of(JourneyStepKey.INTRO_PICKUPS, in.user().isVerified(), JourneyStepStatus.AVAILABLE, book,
                    "P21D");
        }
        boolean done = in.user().isVerified() || s.stage() == Stage.COMPLETE || s.stage() == Stage.VERIFIED;
        JourneyStepStatus active;
        if (s.nextStep() == NextStep.ATTEND_BOOKED) {
            active = JourneyStepStatus.WAITING_FOR_OTHERS;
        } else if (s.counted() + s.upcoming() > 0) {
            active = JourneyStepStatus.IN_PROGRESS;
        } else {
            active = JourneyStepStatus.AVAILABLE;
        }
        Draft draft = Draft.of(JourneyStepKey.INTRO_PICKUPS, done, active,
                        active == JourneyStepStatus.WAITING_FOR_OTHERS
                                ? new Action("onboarding.action.myIntroPickups", "/intro-pickups/my") : book, "P21D")
                .progress(new Progress(s.counted(), s.upcoming(), s.target(), s.fairteilerDone()));
        if (active == JourneyStepStatus.WAITING_FOR_OTHERS) {
            draft = draft.waiting(new WaitingFor("onboarding.waiting.introPickupDates", List.of(), null));
        }
        if (s.nextStep() == NextStep.BOOK_FAIRTEILER_EINAB) {
            draft = draft.blocked("FAIRTEILER_VISIT_REQUIRED", Map.of());
        }
        return draft.details(Map.of("fairteilerRequired", s.fairteilerRequired(), "stalled", s.stalled()));
    }

    private static List<Track> tracks(Inputs in) {
        User user = in.user();
        TrackInputs t = in.tracks();
        List<Track> tracks = new ArrayList<>();
        boolean hygieneDone = t.hygiene() != null && t.hygiene().passValid();
        tracks.add(track("HYGIENE", hygieneDone ? JourneyStepStatus.DONE : JourneyStepStatus.AVAILABLE,
                new Action("onboarding.action.startQuiz", "/quizzes/HYGIENE"), "PT20M"));
        JourneyStepStatus store = PermissionService.isAtLeast(user.getRole(), Role.STORE_MANAGER)
                ? JourneyStepStatus.DONE : user.isVerified() ? quizStatus(t.storeManager()) : JourneyStepStatus.LOCKED;
        tracks.add(track("STORE_MANAGER", store, new Action("onboarding.action.startQuiz", "/quizzes/STORE_MANAGER"),
                "PT30M"));
        JourneyStepStatus guide = t.guide() ? JourneyStepStatus.DONE
                : user.isVerified() ? JourneyStepStatus.WAITING_FOR_OTHERS : JourneyStepStatus.LOCKED;
        tracks.add(new Track("GUIDE", List.of(new StepView("GUIDE", guide, titleKey("GUIDE"), null, null, null, "P14D",
                null, null, guide == JourneyStepStatus.WAITING_FOR_OTHERS
                ? new WaitingFor("onboarding.waiting.guidePermission", in.verificationContacts(), null) : null,
                true, null))));
        JourneyStepStatus ambassador = PermissionService.isAtLeast(user.getRole(), Role.AMBASSADOR)
                ? JourneyStepStatus.DONE : PermissionService.isAtLeast(user.getRole(), Role.STORE_MANAGER)
                ? quizStatus(t.ambassador()) : JourneyStepStatus.LOCKED;
        tracks.add(track("AMBASSADOR", ambassador, new Action("onboarding.action.startQuiz", "/quizzes/AMBASSADOR"),
                "PT45M"));
        return tracks;
    }

    private static JourneyStepStatus quizStatus(TryState state) {
        if (state == null || state.lastStatus() == null) {
            return JourneyStepStatus.AVAILABLE;
        }
        return state.disqualified() ? JourneyStepStatus.WAITING_FOR_OTHERS : JourneyStepStatus.IN_PROGRESS;
    }

    private static Track track(String key, JourneyStepStatus status, Action action, String duration) {
        boolean active = status == JourneyStepStatus.AVAILABLE || status == JourneyStepStatus.IN_PROGRESS;
        return new Track(key, List.of(new StepView(key, status, titleKey(key), null, null, active ? action : null,
                duration, null, null, null, true, null)));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
