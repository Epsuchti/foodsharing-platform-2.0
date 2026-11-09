package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.onboarding.domain.LearningProgress;
import network.foodsharing.onboarding.repository.LearningProgressRepository;
import network.foodsharing.onboarding.repository.UserOnboardingStepDoneRepository;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.onboarding.service.JourneyCalculator.StepView;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.context.ApplicationEventPublisher;

/** C1: only required chapters count for LEARN and learningDone; rereading records no second history entry. */
class JourneyServiceLearningTest {

    private static final List<String> REQUIRED = List.of("principles", "code-of-conduct");
    private static final List<String> ALL = List.of("principles", "code-of-conduct", "rule-violations-explained");

    private final UserService users = mock(UserService.class);
    private final ContentService content = mock(ContentService.class);
    private final LearningProgressRepository learning = mock(LearningProgressRepository.class);
    private final CurrentActorService actor = mock(CurrentActorService.class);
    private final HistoryService history = mock(HistoryService.class, Answers.RETURNS_DEEP_STUBS);
    private final QuizSessionService quizzes = mock(QuizSessionService.class);
    private final List<LearningProgress> rows = new ArrayList<>();
    private final User user = new User();
    private JourneyService service;

    @BeforeEach
    void setUp() {
        service = new JourneyService(users, mock(RegionService.class), mock(RegionReadService.class),
                mock(RegionSettingsService.class), mock(RegionPermissionResolver.class), mock(MembershipService.class),
                content, mock(LegalService.class), quizzes, mock(IntroPickupProgressService.class), learning,
                mock(UserOnboardingStepDoneRepository.class), actor, mock(PermissionService.class), history,
                mock(HistoryQueryService.class), mock(ApplicationEventPublisher.class),
                Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC), mock(MemberPickupStats.class));
        user.setId(UUID.randomUUID());
        user.setRole(Role.FOODSHARER);
        when(actor.requireUserId()).thenReturn(user.getId());
        when(users.require(user.getId())).thenReturn(user);
        when(quizzes.tryStatesOf(user.getId())).thenReturn(Map.of());
        when(content.learningChapterKeys()).thenReturn(ALL);
        when(content.requiredChapterKeys()).thenReturn(REQUIRED);
        when(learning.findByUserId(user.getId())).thenAnswer(call -> List.copyOf(rows));
        when(learning.existsByUserIdAndChapterKey(any(), any())).thenAnswer(call -> rows.stream()
                .anyMatch(row -> row.getChapterKey().equals(call.getArgument(1))));
        when(learning.save(any())).thenAnswer(call -> {
            rows.add(call.getArgument(0));
            return call.getArgument(0);
        });
    }

    private static StepView learn(JourneyView journey) {
        return journey.steps().stream().filter(s -> s.key().equals("LEARN")).findFirst().orElseThrow();
    }

    @Test
    void readingAnOptionalChapterKeepsTheProgress() {
        JourneyView journey = service.markChapterRead("rule-violations-explained");
        assertThat(rows).hasSize(1);
        assertThat(learn(journey).progress().done()).isZero();
        assertThat(learn(journey).progress().required()).isEqualTo(2);
        assertThat(service.learningDone(user.getId())).isFalse();
    }

    @Test
    void requiredChaptersFinishLearningAndASecondReadRecordsNothing() {
        service.markChapterRead("principles");
        service.markChapterRead("principles");
        verify(learning, times(1)).save(any());
        verify(history, times(1)).record(any());
        assertThat(service.learningDone(user.getId())).isFalse();
        JourneyView journey = service.markChapterRead("code-of-conduct");
        assertThat(learn(journey).status()).isEqualTo(network.foodsharing.onboarding.domain.JourneyStepStatus.DONE);
        assertThat(service.learningDone(user.getId())).isTrue();
    }

    @Test
    void unknownChapterIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markChapterRead("nope"))
                .isInstanceOf(network.foodsharing.common.error.ApiException.class);
        verify(learning, never()).save(any());
    }
}
