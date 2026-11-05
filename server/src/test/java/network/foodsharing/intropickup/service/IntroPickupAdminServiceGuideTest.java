package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.repository.IntroPickupCommentRepository;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupStoreAliasRepository;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizRuleService.TryState;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Q4 (decision 167): release needs a region administrator and an EinAb guide quiz pass; candidates list. */
class IntroPickupAdminServiceGuideTest {

    private static final UUID REGION = UUID.randomUUID();
    private static final UUID CANDIDATE = UUID.randomUUID();
    private static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            Role.AMBASSADOR, "actor@test");

    private final RegionMembersService members = mock(RegionMembersService.class);
    private final RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final QuizSessionService quizzes = mock(QuizSessionService.class);
    private final MembershipService memberships = mock(MembershipService.class);
    private final MemberPickupStats pickupStats = mock(MemberPickupStats.class);
    private final IntroPickupRepository pickups = mock(IntroPickupRepository.class);
    private final IntroPickupViews views = mock(IntroPickupViews.class);

    private final IntroPickupAdminService service = new IntroPickupAdminService(pickups,
            mock(IntroPickupSlotRepository.class), mock(IntroPickupCommentRepository.class),
            mock(IntroPickupStoreAliasRepository.class), views, mock(IntroPickupGuideService.class),
            mock(RegionService.class), members, resolver, users, currentActor, permissions, history,
            mock(ApplicationEventPublisher.class), quizzes, memberships, pickupStats);

    private static User user(UUID id, String name) {
        User user = new User();
        user.setId(id);
        user.setDisplayName(name);
        user.setActive(true);
        return user;
    }

    private static TryState passed() {
        return new TryState(QuizSessionStatus.PASSED, 0, 0, 0, Instant.parse("2026-11-01T10:00:00Z"), null, true);
    }

    @BeforeEach
    void setUp() {
        when(currentActor.requirePrincipal()).thenReturn(ACTOR);
        when(users.require(CANDIDATE)).thenReturn(user(CANDIDATE, "Jonas Weber"));
        when(resolver.isMember(CANDIDATE, REGION)).thenReturn(true);
        when(views.now()).thenReturn(Instant.parse("2026-11-05T10:00:00Z"));
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void guideAdminsWithoutRegionAdministrationMayNotRelease() {
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateRegion(ACTOR, REGION);
        when(quizzes.tryStateOf(CANDIDATE, QuizKind.EINAB_GUIDE)).thenReturn(Optional.of(passed()));
        assertThatThrownBy(() -> service.setGuideEnabled(REGION, CANDIDATE, true, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        verify(members, never()).releaseGuide(any(), any());
    }

    @Test
    void releaseNeedsAValidQuizPass() {
        when(quizzes.tryStateOf(CANDIDATE, QuizKind.EINAB_GUIDE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setGuideEnabled(REGION, CANDIDATE, true, "ok"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.GUIDE_QUIZ_NOT_PASSED));
        when(quizzes.tryStateOf(CANDIDATE, QuizKind.EINAB_GUIDE)).thenReturn(Optional.of(
                new TryState(QuizSessionStatus.FAILED, 1, 0, 0, null, null, false)));
        assertThatThrownBy(() -> service.setGuideEnabled(REGION, CANDIDATE, true, "ok"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.GUIDE_QUIZ_NOT_PASSED));
        verify(members, never()).releaseGuide(any(), any());
    }

    @Test
    void releaseNeedsRegionMembership() {
        when(resolver.isMember(CANDIDATE, REGION)).thenReturn(false);
        when(quizzes.tryStateOf(CANDIDATE, QuizKind.EINAB_GUIDE)).thenReturn(Optional.of(passed()));
        assertThatThrownBy(() -> service.setGuideEnabled(REGION, CANDIDATE, true, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_REGION_MEMBER));
    }

    @Test
    void regionAdministratorsReleasePeopleWithAPass() {
        when(quizzes.tryStateOf(CANDIDATE, QuizKind.EINAB_GUIDE)).thenReturn(Optional.of(passed()));
        service.setGuideEnabled(REGION, CANDIDATE, true, "  ");
        verify(members).releaseGuide(REGION, CANDIDATE);
        verify(history).record(argThat(event -> event.action() == IntroPickupAction.GUIDE_ENABLED
                && REGION.equals(event.regionId()) && event.reason() == null));
    }

    @Test
    void guideAdminsMayStillDisable() {
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateRegion(ACTOR, REGION);
        service.setGuideEnabled(REGION, CANDIDATE, false, "Pause");
        verify(permissions).require(ACTOR, Permission.CAN_MANAGE_USERS, REGION);
        verify(members).withdrawGuide(REGION, CANDIDATE);
        verify(history).record(argThat(event -> event.action() == IntroPickupAction.GUIDE_DISABLED
                && "Pause".equals(event.reason())));
    }

    @Test
    void candidatesAreMembersWithAPassWhoAreNotReleasedYet() {
        UUID released = UUID.randomUUID();
        UUID without = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        when(resolver.holders(Permission.CAN_GIVE_INTRO_PICKUPS, REGION)).thenReturn(Set.of(released));
        when(memberships.membersOf(REGION)).thenReturn(List.of(released, CANDIDATE, without, later));
        when(quizzes.validPasses(argThat(ids -> ids.size() == 3 && !ids.contains(released)), eq(QuizKind.EINAB_GUIDE)))
                .thenReturn(Map.of(CANDIDATE, Instant.parse("2026-11-01T10:00:00Z"),
                        later, Instant.parse("2026-11-03T10:00:00Z")));
        when(users.findActive(any(java.util.Collection.class)))
                .thenReturn(List.of(user(later, "Anna"), user(CANDIDATE, "Jonas Weber")));
        when(pickupStats.pickupCount(CANDIDATE)).thenReturn(31);

        var page = service.guideCandidates(REGION, null, null);
        assertThat(page.getContent()).extracting(view -> view.user().getId()).containsExactly(CANDIDATE, later);
        assertThat(page.getContent().getFirst().pickupCount()).isEqualTo(31);
        verify(permissions).require(ACTOR, Permission.CAN_MANAGE_USERS, REGION);
    }
}
