package network.foodsharing.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.content.service.LegalService;
import network.foodsharing.intropickup.service.IntroPickupProgressService;
import network.foodsharing.intropickup.service.NewcomerProgressService.NextStep;
import network.foodsharing.intropickup.service.NewcomerProgressService.Stage;
import network.foodsharing.onboarding.service.JourneyService;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.verification.service.VerificationQueueService.Candidate;
import network.foodsharing.verification.service.VerificationQueueService.Filter;
import org.junit.jupiter.api.Test;

/**
 * Ve2 / D10.3 verification queue (rule the old e2e spec asserted, decision 180): READY lists unverified members whose
 * EinAb journey is COMPLETE and who accepted the legal agreement; "all unverified" adds the others; verified members
 * never show up; only administrators of the region see the queue.
 */
class VerificationQueueServiceTest {

    private static final UUID REGION = UUID.randomUUID();

    private final MembershipService memberships = mock(MembershipService.class);
    private final UserService users = mock(UserService.class);
    private final IntroPickupProgressService progress = mock(IntroPickupProgressService.class);
    private final LegalService legal = mock(LegalService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final VerificationQueueService queue = new VerificationQueueService(mock(RegionService.class), memberships,
            users, progress, legal, mock(QuizSessionService.class), currentActor, permissions, mock(JourneyService.class));

    private final User ready = member("ready@local", Stage.COMPLETE, true);
    private final User noAgreement = member("noagreement@local", Stage.COMPLETE, false);
    private final User inProgress = member("progress@local", Stage.IN_PROGRESS, true);
    private final User verified = member("verified@local", Stage.COMPLETE, true);
    private final SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR,
            "bot@local");

    VerificationQueueServiceTest() {
        verified.setVerified(true);
        List<User> all = List.of(ready, noAgreement, inProgress, verified);
        List<UUID> ids = all.stream().map(User::getId).toList();
        when(memberships.membersOf(REGION)).thenReturn(ids);
        when(users.findActive(ids)).thenReturn(all);
        when(currentActor.requirePrincipal()).thenReturn(actor);
    }

    private User member(String email, Stage stage, boolean agreed) {
        User user = TestUsers.user(email, Role.FOODSHARER);
        user.setId(UUID.randomUUID());
        when(progress.progressOf(user.getId(), REGION)).thenReturn(new IntroPickupProgressService.Summary(stage,
                NextStep.DONE, 3, 0, 3, false, false, false, false, null));
        when(legal.hasCurrentConsent(user.getId(), LegalDocumentKind.LEGAL_AGREEMENT)).thenReturn(agreed);
        return user;
    }

    private List<User> listed(Filter filter) {
        return queue.queue(REGION, filter, null, null, null).getContent().stream().map(Candidate::user).toList();
    }

    @Test
    void readyNeedsACompleteEinAbJourneyAndTheAgreement() {
        assertThat(listed(Filter.READY)).containsExactly(ready);
    }

    @Test
    void allUnverifiedAddsTheOthersButNeverTheVerified() {
        assertThat(listed(Filter.ALL_UNVERIFIED)).containsExactlyInAnyOrder(ready, noAgreement, inProgress);
    }

    @Test
    void onlyAdministratorsOfTheRegionSeeTheQueue() {
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateRegion(any(), any());
        assertThatThrownBy(() -> listed(Filter.READY)).isInstanceOf(ApiException.class);
    }
}
