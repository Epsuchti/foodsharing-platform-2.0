package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.onboarding.domain.OnboardingAction;
import network.foodsharing.onboarding.domain.UserOnboardingStepDone;
import network.foodsharing.onboarding.repository.LearningProgressRepository;
import network.foodsharing.onboarding.repository.UserOnboardingStepDoneRepository;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Region extra steps (plan §11, e.g. {@code newcomer-meeting}): ambassadors, ORGA and CAN_MANAGE_USERS holders confirm
 * them for members of the region, once (a second confirmation changes nothing), and the newcomer is told after commit.
 * The journey records JOURNEY_COMPLETED only the first time it is complete.
 */
class JourneyServiceRegionStepTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final UserService users = mock(UserService.class);
    private final RegionService regions = mock(RegionService.class);
    private final RegionSettingsService settings = mock(RegionSettingsService.class);
    private final RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    private final UserOnboardingStepDoneRepository stepsDone = mock(UserOnboardingStepDoneRepository.class);
    private final CurrentActorService actor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final HistoryQueryService historyQuery = mock(HistoryQueryService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final List<HistoryEvent> recorded = new ArrayList<>();
    private final List<UserOnboardingStepDone> saved = new ArrayList<>();
    private JourneyService service;

    private final UUID region = UUID.randomUUID();
    private final UUID newcomer = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();
    private final SessionPrincipal welcome = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Role.AMBASSADOR, "welcome@test.local");

    @BeforeEach
    void setUp() {
        service = new JourneyService(users, regions, mock(RegionReadService.class), settings, resolver,
                mock(MembershipService.class), mock(ContentService.class), mock(LegalService.class),
                mock(QuizSessionService.class), mock(IntroPickupProgressService.class),
                mock(LearningProgressRepository.class), stepsDone, actor, permissions, history, historyQuery, events,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(MemberPickupStats.class));
        when(regions.require(region)).thenReturn(new Region());
        when(actor.requirePrincipal()).thenReturn(welcome);
        when(permissions.isAmbassadorOrOrga(welcome, region)).thenReturn(true);
        when(settings.steps(region)).thenReturn(List.of(new RegionSettingsService.Step(stepId, "newcomer-meeting",
                Map.of(), Map.of(), 0)));
        when(users.require(newcomer)).thenReturn(new User());
        when(resolver.isMember(newcomer, region)).thenReturn(true);
        when(stepsDone.existsByUserIdAndStepId(any(), any())).thenAnswer(call -> saved.stream()
                .anyMatch(done -> done.getUserId().equals(call.getArgument(0))
                        && done.getStepId().equals(call.getArgument(1))));
        when(stepsDone.save(any())).thenAnswer(call -> {
            saved.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(history.event(any(HistoryAction.class), any())).thenAnswer(call -> HistoryEvent.builder(
                call.getArgument(0), call.getArgument(1)));
        when(history.record(any())).thenAnswer(call -> {
            recorded.add(call.getArgument(0));
            return null;
        });
    }

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    @Test
    void anAmbassadorConfirmsTheStepOnceAndTheNewcomerIsTold() {
        service.confirmRegionStep(region, newcomer, "newcomer-meeting");
        service.confirmRegionStep(region, newcomer, "newcomer-meeting");

        assertThat(saved).singleElement().satisfies(done -> {
            assertThat(done.getConfirmedBy()).isEqualTo(welcome.userId());
            assertThat(done.getConfirmedAt()).isEqualTo(NOW);
        });
        assertThat(recorded).extracting(HistoryEvent::action).containsExactly(OnboardingAction.REGION_STEP_CONFIRMED);
        verify(events, times(1)).publishEvent(new JourneyService.RegionStepConfirmed(newcomer, region,
                "newcomer-meeting"));
    }

    @Test
    void plainMembersMayNotConfirmButCanManageUsersHoldersMay() {
        SessionPrincipal foodsaver = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER,
                "fs@test.local");
        when(actor.requirePrincipal()).thenReturn(foodsaver);
        assertThat(code(() -> service.confirmRegionStep(region, newcomer, "newcomer-meeting")))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(permissions.has(foodsaver, Permission.CAN_MANAGE_USERS, region)).thenReturn(true);
        assertThat(code(() -> service.confirmRegionStep(region, newcomer, "newcomer-meeting"))).isNull();
    }

    @Test
    void unknownStepsAndNonMembersAreRefused() {
        assertThat(code(() -> service.confirmRegionStep(region, newcomer, "unknown")))
                .isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
        UUID stranger = UUID.randomUUID();
        when(users.require(stranger)).thenReturn(new User());
        assertThat(code(() -> service.confirmRegionStep(region, stranger, "newcomer-meeting")))
                .isEqualTo(ApiErrorCode.NOT_A_MEMBER);
        assertThat(saved).isEmpty();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void theCompletedJourneyIsRecordedOnlyTheFirstTime() {
        JourneyService spied = spy(service);
        when(actor.requireUserId()).thenReturn(newcomer);
        doReturn(new JourneyView(true, null, List.of(), null, List.of(), List.of())).when(spied).compute(newcomer);
        when(historyQuery.list(any())).thenReturn(Page.empty());

        assertThat(spied.mine().complete()).isTrue();
        assertThat(recorded).extracting(HistoryEvent::action).containsExactly(OnboardingAction.JOURNEY_COMPLETED);

        Page<HistoryEntry> already = new PageImpl<>(List.of(new HistoryEntry()));
        when(historyQuery.list(any())).thenReturn(already);
        spied.mine();
        assertThat(recorded).hasSize(1);
    }
}
