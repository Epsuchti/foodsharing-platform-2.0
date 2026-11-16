package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The store log of the team workflow (B3, formerly asserted by StoreTeamFlowIT): every step writes its action with the
 * person as secondary subject (REQUEST_TO_JOIN, REQUEST_APPROVED, MOVED_TO_JUMPER, MOVED_TO_TEAM, REMOVED_FROM_STORE,
 * LEFT_STORE), moving someone to the standby list needs a message that reaches them as STORE_MOVED_TO_STANDBY, and a
 * withdrawn invitation takes its STORE_INVITATION bell back.
 */
class StoreTeamLogTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.fromString("00000020-0001-4000-8000-000000000005");
    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-000000000002");

    StoreService stores = mock(StoreService.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    StorePickupInfo pickupInfo = mock(StorePickupInfo.class);
    UserService users = mock(UserService.class);
    RegionReadService regionReads = mock(RegionReadService.class);
    QuizSessionService quizzes = mock(QuizSessionService.class);
    RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    List<Object> published = new ArrayList<>();
    List<StoreMembership> team = new ArrayList<>();
    StoreTeamService service;
    User me;

    @BeforeEach
    void setUp() {
        service = new StoreTeamService(stores, memberships, rights, pickupInfo, users, quizzes,
                mock(MembershipService.class), resolver, regionReads,
                mock(RegionMembersService.class), published::add, currentActor, history, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(TeamChatPort.class));
        Store store = new Store();
        store.setId(STORE);
        store.setName("Bäckerei");
        store.setRegionId(REGION);
        store.setTeamStatus(TeamSearchStatus.OPEN);
        when(stores.require(STORE)).thenReturn(store);
        me = new User();
        me.setId(UUID.randomUUID());
        me.setRole(Role.STORE_MANAGER);
        me.setDisplayName("Nina");
        me.setVerified(true);
        me.setFirstName("Nina");
        me.setLastName("Schneider");
        me.setStreet("Langstrasse 1");
        me.setPostcode("8004");
        me.setCity("Zürich");
        me.setHomeRegionId(REGION);
        me.setMobilePhone("+41790000000");
        when(resolver.isMember(me.getId(), REGION)).thenReturn(true);
        when(quizzes.hygieneValidUntil(any(UUID.class))).thenReturn(Optional.of(NOW.plus(30, ChronoUnit.DAYS)));
        when(currentActor.requireUser()).thenReturn(me);
        when(currentActor.requireUserId()).thenReturn(me.getId());
        when(currentActor.currentUserId()).thenReturn(Optional.of(me.getId()));
        when(currentActor.requirePrincipal())
                .thenAnswer(i -> new SessionPrincipal(me.getId(), UUID.randomUUID(), me.getRole(), "bieb@local"));
        when(memberships.findByStoreId(STORE)).thenReturn(team);
        when(rights.rights(any(), any())).thenReturn(new StorePermissionService.Rights(true, true, true, false, true,
                true, true, true, true, true, true, true, true, false, false, false, true, true, false, true, false,
                UserTeamStatus.COORDINATOR));
        when(rights.teamStatus(any(), any())).thenReturn(UserTeamStatus.NO_MEMBER);
    }

    private StoreMembership member(StoreMembershipStatus status) {
        StoreMembership m = new StoreMembership();
        m.setStoreId(STORE);
        m.setUserId(UUID.randomUUID());
        m.setStatus(status);
        team.add(m);
        when(memberships.findByStoreIdAndUserId(STORE, m.getUserId())).thenReturn(Optional.of(m));
        User user = new User();
        user.setId(m.getUserId());
        user.setVerified(true);
        when(users.require(m.getUserId())).thenReturn(user);
        return m;
    }

    private void logged(StoreLogAction action) {
        verify(history).event(action, STORE);
    }

    private boolean rang(NotificationType type, UUID recipient) {
        return published.stream().filter(StoreNotifications.Send.class::isInstance)
                .map(e -> ((StoreNotifications.Send) e).request())
                .anyMatch(r -> r.type() == type && r.recipientIds().contains(recipient));
    }

    private static ApiErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e.getCode();
        }
        return null;
    }

    @Test
    void applyingIsARequestToJoin() {
        member(StoreMembershipStatus.MEMBER).setManager(true);
        service.apply(STORE, "Gerne!");
        logged(StoreLogAction.REQUEST_TO_JOIN);
    }

    @Test
    void acceptingOntoTheStandbyListIsApprovedAndMovedToJumper() {
        StoreMembership applied = member(StoreMembershipStatus.APPLIED);
        service.accept(STORE, applied.getUserId(), true);
        assertThat(applied.getStatus()).isEqualTo(StoreMembershipStatus.JUMPER);
        logged(StoreLogAction.REQUEST_APPROVED);
        logged(StoreLogAction.MOVED_TO_JUMPER);
    }

    @Test
    void theStandbyListNeedsAMessageWhichReachesThePerson() {
        StoreMembership m = member(StoreMembershipStatus.MEMBER);
        assertThat(codeOf(() -> service.moveToStandby(STORE, m.getUserId(), ""))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(m.getStatus()).isEqualTo(StoreMembershipStatus.MEMBER);
        verify(history, never()).event(StoreLogAction.MOVED_TO_JUMPER, STORE);

        service.moveToStandby(STORE, m.getUserId(), "Pause bis Ende Monat");
        assertThat(m.getStatus()).isEqualTo(StoreMembershipStatus.JUMPER);
        logged(StoreLogAction.MOVED_TO_JUMPER);
        assertThat(rang(NotificationType.STORE_MOVED_TO_STANDBY, m.getUserId())).isTrue();

        service.moveToTeam(STORE, m.getUserId());
        assertThat(m.getStatus()).isEqualTo(StoreMembershipStatus.MEMBER);
        logged(StoreLogAction.MOVED_TO_TEAM);
    }

    @Test
    void withdrawingAnInvitationTakesItsBellBack() {
        StoreMembership invited = member(StoreMembershipStatus.INVITED);
        service.withdrawInvitation(STORE, invited.getUserId());
        assertThat(published).contains(new StoreNotifications.RemoveBells(network.foodsharing.notification.service
                .BellService.identifier(NotificationType.STORE_INVITATION, STORE, invited.getUserId())));
        logged(StoreLogAction.INVITATION_WITHDRAWN);
    }

    @Test
    void removingSomeoneIsRemovedFromStoreLeavingIsLeftStore() {
        StoreMembership other = member(StoreMembershipStatus.MEMBER);
        service.remove(STORE, other.getUserId(), "Mehrmals nicht erschienen");
        logged(StoreLogAction.REMOVED_FROM_STORE);
        assertThat(rang(NotificationType.STORE_MEMBER_REMOVED, other.getUserId())).isTrue();

        StoreMembership self = member(StoreMembershipStatus.MEMBER);
        me.setId(self.getUserId());
        service.remove(STORE, self.getUserId(), null);
        logged(StoreLogAction.LEFT_STORE);
    }
}
