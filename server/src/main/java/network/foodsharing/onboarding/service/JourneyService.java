package network.foodsharing.onboarding.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.onboarding.domain.LearningProgress;
import network.foodsharing.onboarding.domain.OnboardingAction;
import network.foodsharing.onboarding.domain.UserOnboardingStepDone;
import network.foodsharing.onboarding.repository.LearningProgressRepository;
import network.foodsharing.onboarding.repository.UserOnboardingStepDoneRepository;
import network.foodsharing.onboarding.service.JourneyCalculator.ExtraStep;
import network.foodsharing.onboarding.service.JourneyCalculator.Inputs;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.onboarding.service.JourneyCalculator.TrackInputs;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.PhoneNumberValidator;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The onboarding journey (plan §11.1): collects the state from the other modules (read-only service calls) and lets
 * {@link JourneyCalculator} derive the steps; records learning progress, confirmations of region extra steps and the
 * one-time JOURNEY_COMPLETED.
 */
@Service
public class JourneyService {

    /** After commit: ONBOARDING_STEP_UNBLOCKED to the newcomer. */
    public record RegionStepConfirmed(UUID userId, UUID regionId, String stepKey) {
    }

    static final Duration TYPICAL_WINDOW = Duration.ofDays(365);

    private final UserService users;
    private final RegionService regions;
    private final RegionReadService regionReads;
    private final RegionSettingsService regionSettings;
    private final RegionPermissionResolver resolver;
    private final MembershipService memberships;
    private final ContentService content;
    private final LegalService legal;
    private final QuizSessionService quizzes;
    private final IntroPickupProgressService introPickups;
    private final LearningProgressRepository learning;
    private final UserOnboardingStepDoneRepository stepsDone;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final HistoryQueryService historyQuery;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JourneyService(UserService users, RegionService regions, RegionReadService regionReads,
                          RegionSettingsService regionSettings, RegionPermissionResolver resolver,
                          MembershipService memberships, ContentService content, LegalService legal,
                          QuizSessionService quizzes, IntroPickupProgressService introPickups,
                          LearningProgressRepository learning, UserOnboardingStepDoneRepository stepsDone,
                          CurrentActorService currentActor, PermissionService permissions, HistoryService history,
                          HistoryQueryService historyQuery, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.regions = regions;
        this.regionReads = regionReads;
        this.regionSettings = regionSettings;
        this.resolver = resolver;
        this.memberships = memberships;
        this.content = content;
        this.legal = legal;
        this.quizzes = quizzes;
        this.introPickups = introPickups;
        this.learning = learning;
        this.stepsDone = stepsDone;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.historyQuery = historyQuery;
        this.events = events;
        this.clock = clock;
    }

    /** The journey of a user (no permission check; callers check). */
    @Transactional(readOnly = true)
    public JourneyView compute(UUID userId) {
        return JourneyCalculator.compute(inputs(users.require(userId)));
    }

    /** The current user's journey; records JOURNEY_COMPLETED the first time it is complete. */
    @Transactional
    public JourneyView mine() {
        UUID userId = currentActor.requireUserId();
        JourneyView journey = compute(userId);
        if (journey.complete() && historyQuery.list(HistoryFilter.forSubject(SubjectType.USER, userId,
                List.of(OnboardingAction.JOURNEY_COMPLETED.code()), 0, 1)).isEmpty()) {
            history.record(history.event(OnboardingAction.JOURNEY_COMPLETED, userId).build());
        }
        return journey;
    }

    /** @throws ApiException 404 CONTENT_NOT_FOUND */
    @Transactional
    public JourneyView markChapterRead(String key) {
        UUID userId = currentActor.requireUserId();
        if (!content.learningChapterKeys().contains(key)) {
            throw ApiException.notFound(ApiErrorCode.CONTENT_NOT_FOUND);
        }
        if (!learning.existsByUserIdAndChapterKey(userId, key)) {
            LearningProgress progress = new LearningProgress();
            progress.setUserId(userId);
            progress.setChapterKey(key);
            progress.setReadAt(clock.instant());
            learning.save(progress);
            history.record(history.event(OnboardingAction.CHAPTER_READ, userId).metadata("chapter", key).build());
        }
        return compute(userId);
    }

    /**
     * Confirms a region extra step for a member (ambassadors, ORGA, CAN_MANAGE_USERS holders).
     *
     * @throws ApiException 404 REGION_NOT_FOUND / USER_NOT_FOUND / RESOURCE_NOT_FOUND, 403, 409 NOT_A_MEMBER
     */
    @Transactional
    public void confirmRegionStep(UUID regionId, UUID userId, String stepKey) {
        regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.isAmbassadorOrOrga(actor, regionId)
                && !permissions.has(actor, Permission.CAN_MANAGE_USERS, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        RegionSettingsService.Step step = regionSettings.steps(regionId).stream()
                .filter(s -> s.key().equals(stepKey)).findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
        users.require(userId);
        if (!resolver.isMember(userId, regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_A_MEMBER);
        }
        if (stepsDone.existsByUserIdAndStepId(userId, step.id())) {
            return;
        }
        UserOnboardingStepDone done = new UserOnboardingStepDone();
        done.setUserId(userId);
        done.setStepId(step.id());
        done.setConfirmedBy(actor.userId());
        done.setConfirmedAt(clock.instant());
        stepsDone.save(done);
        history.record(history.event(OnboardingAction.REGION_STEP_CONFIRMED, userId).region(regionId)
                .metadata("step", stepKey).build());
        events.publishEvent(new RegionStepConfirmed(userId, regionId, stepKey));
    }

    /** Has the user read every published learning chapter? (verification queue) */
    @Transactional(readOnly = true)
    public boolean learningDone(UUID userId) {
        Set<String> read = learning.findByUserId(userId).stream().map(LearningProgress::getChapterKey)
                .collect(Collectors.toSet());
        return read.containsAll(content.learningChapterKeys());
    }

    /** Latest progress timestamp of the user (nudges): registration, chapters, confirmations, EinAbs, quiz. */
    @Transactional(readOnly = true)
    public Instant lastProgressAt(User user) {
        List<Instant> times = new ArrayList<>();
        times.add(user.getRegisteredAt() != null ? user.getRegisteredAt() : user.getCreatedAt());
        learning.findByUserId(user.getId()).forEach(p -> times.add(p.getReadAt()));
        stepsDone.findByUserId(user.getId()).forEach(s -> times.add(s.getConfirmedAt()));
        quizzes.passedAt(user.getId(), QuizKind.FOODSAVER).ifPresent(times::add);
        if (user.getHomeRegionId() != null) {
            times.add(introPickups.progressOf(user.getId(), user.getHomeRegionId()).lastActivityAt());
        }
        return times.stream().filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(user.getCreatedAt());
    }

    // ------------------------------------------------------------------------------------------- inputs

    Inputs inputs(User user) {
        Region region = user.getHomeRegionId() == null ? null : regions.find(user.getHomeRegionId()).orElse(null);
        RegionSettingsService.Settings settings = region == null ? null : regionSettings.read(region);
        Set<String> read = learning.findByUserId(user.getId()).stream().map(LearningProgress::getChapterKey)
                .collect(Collectors.toSet());
        List<ExtraStep> extras = new ArrayList<>();
        if (settings != null) {
            Set<UUID> confirmed = stepsDone.findByUserId(user.getId()).stream().map(UserOnboardingStepDone::getStepId)
                    .collect(Collectors.toSet());
            String language = user.getPreferredLanguage().getValue();
            for (RegionSettingsService.Step step : settings.onboarding().extraSteps()) {
                String title = step.titles().getOrDefault(language, step.titles().getOrDefault("de", step.key()));
                extras.add(new ExtraStep(step.id(), step.key(), title, confirmed.contains(step.id())));
            }
        }
        return new Inputs(user, PhoneNumberValidator.isValid(user.getMobilePhone()), region,
                settings == null || settings.onboarding().learningPagesMandatory(), content.learningChapterKeys(),
                read, quizzes.tryStateOf(user.getId(), QuizKind.FOODSAVER).orElse(null), quizContacts(),
                legal.hasCurrentConsent(user.getId(), LegalDocumentKind.LEGAL_AGREEMENT),
                region == null ? null : introPickups.progressOf(user.getId(), region.getId()), extras,
                region == null ? List.of() : verificationContacts(region.getId()),
                region == null ? null : typicalVerificationDays(region.getId()),
                new TrackInputs(quizzes.tryStateOf(user.getId(), QuizKind.HYGIENE).orElse(null),
                        quizzes.tryStateOf(user.getId(), QuizKind.STORE_MANAGER).orElse(null),
                        quizzes.tryStateOf(user.getId(), QuizKind.AMBASSADOR).orElse(null),
                        region != null && resolver.hasRegionPermission(user.getId(),
                                Permission.CAN_GIVE_INTRO_PICKUPS, region.getId())));
    }

    /** Admins of the region's FSMANAGEMENT group, else its ambassadors. */
    List<User> verificationContacts(UUID regionId) {
        return regionReads.functionGroups(regionId).stream()
                .filter(g -> g.function() == WorkingGroupFunction.FSMANAGEMENT).findFirst()
                .map(g -> regionReads.ambassadorsOf(g.group().getId())).filter(list -> !list.isEmpty())
                .orElseGet(() -> regionReads.ambassadorsOf(regionId));
    }

    /** Admins of the working groups that run the foodsaver quiz. */
    List<User> quizContacts() {
        return regions.groupsWithQuizKind(QuizKind.FOODSAVER.name()).stream()
                .flatMap(g -> regionReads.ambassadorsOf(g.getId()).stream()).distinct().toList();
    }

    /** Median days from registration to verification of the region's members verified in the last year. */
    Integer typicalVerificationDays(UUID regionId) {
        Instant since = clock.instant().minus(TYPICAL_WINDOW);
        List<Long> days = users.findActive(memberships.membersOf(regionId)).stream()
                .filter(u -> u.isVerified() && u.getVerifiedAt() != null && u.getVerifiedAt().isAfter(since))
                .map(u -> Math.max(0, Duration.between(u.getRegisteredAt() != null ? u.getRegisteredAt()
                        : u.getCreatedAt(), u.getVerifiedAt()).toDays()))
                .sorted().toList();
        if (days.isEmpty()) {
            return null;
        }
        int middle = days.size() / 2;
        return (int) (days.size() % 2 == 1 ? days.get(middle) : (days.get(middle - 1) + days.get(middle)) / 2);
    }
}
