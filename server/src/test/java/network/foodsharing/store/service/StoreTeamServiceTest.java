package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Team workflow rules (A19, B3): requirement order of {@code mayJoinStore}, request recipients, manager limits. */
class StoreTeamServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.fromString("00000020-0001-4000-8000-000000000001");
    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-000000000002");

    StoreService stores = mock(StoreService.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    StorePickupInfo pickupInfo = mock(StorePickupInfo.class);
    UserService users = mock(UserService.class);
    QuizSessionService quizzes = mock(QuizSessionService.class);
    MembershipService regionMemberships = mock(MembershipService.class);
    RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    RegionReadService regionReads = mock(RegionReadService.class);
    RegionMembersService regionMembers = mock(RegionMembersService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    TeamChatPort teamChat = mock(TeamChatPort.class);
    List<Object> published = new ArrayList<>();
    ApplicationEventPublisher events = published::add;
    AppProperties properties = new AppProperties();
    StoreTeamService service;
    Store store;
    User user;
    List<StoreMembership> team = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new StoreTeamService(stores, memberships, rights, pickupInfo, users, quizzes, regionMemberships,
                resolver, regionReads, regionMembers, events, currentActor, history, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), teamChat);
        store = new Store();
        store.setId(STORE);
        store.setName("Coop");
        store.setRegionId(REGION);
        store.setTeamStatus(TeamSearchStatus.OPEN);
        when(stores.require(STORE)).thenReturn(store);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.FOODSAVER);
        user.setFirstName("Fo");
        user.setLastName("Saver");
        user.setDisplayName("Fo");
        user.setStreet("Langstrasse 1");
        user.setPostcode("8004");
        user.setCity("Zürich");
        user.setHomeRegionId(REGION);
        user.setVerified(true);
        user.setMobilePhone("+41790000000");
        when(resolver.isMember(user.getId(), REGION)).thenReturn(true);
        when(quizzes.hygieneValidUntil(any(UUID.class))).thenReturn(Optional.of(NOW.plus(30, ChronoUnit.DAYS)));
        when(quizzes.hygieneValidUntil(org.mockito.ArgumentMatchers.<java.util.Collection<UUID>>any()))
                .thenAnswer(i -> {
                    java.util.Map<UUID, java.time.Instant> valid = new java.util.HashMap<>();
                    ((java.util.Collection<UUID>) i.getArgument(0)).forEach(id -> valid.put(id,
                            NOW.plus(30, ChronoUnit.DAYS)));
                    return valid;
                });
        when(memberships.findByStoreId(STORE)).thenReturn(team);
        when(currentActor.requireUser()).thenReturn(user);
        when(currentActor.requirePrincipal())
                .thenAnswer(i -> new SessionPrincipal(user.getId(), UUID.randomUUID(), user.getRole(), "a@b.c"));
        when(rights.rights(any(), any())).thenReturn(editRights());
        when(rights.teamStatus(any(), any())).thenReturn(UserTeamStatus.NO_MEMBER);
    }

    private ApiErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return (ApiErrorCode) org.springframework.test.util.ReflectionTestUtils.getField(e, "code");
        }
        return null;
    }

    private ApiErrorCode check(String text) {
        return codeOf(() -> service.checkRequirements(store, user, text, true, true));
    }

    @Test
    void requirementsInUpstreamOrder() {
        store.setRequiresVerified(true);
        store.setRequiresPhone(true);
        store.setRequiresHygiene(true);
        store.setRequiresApplicationText(true);
        assertThat(check("Hallo")).isNull();
        assertThat(check(" ")).isEqualTo(ApiErrorCode.APPLICATION_TEXT_REQUIRED);
        when(quizzes.hygieneValidUntil(user.getId())).thenReturn(Optional.of(NOW));
        assertThat(check(" ")).isEqualTo(ApiErrorCode.HYGIENE_CERTIFICATE_REQUIRED);
        user.setMobilePhone(null);
        assertThat(check(" ")).isEqualTo(ApiErrorCode.PHONE_REQUIRED);
        user.setLandlinePhone("+41440000000");
        assertThat(check(" ")).isEqualTo(ApiErrorCode.HYGIENE_CERTIFICATE_REQUIRED);
        user.setLandlinePhone(null);
        user.setVerified(false);
        assertThat(check(" ")).isEqualTo(ApiErrorCode.NOT_VERIFIED);
        when(resolver.isMember(user.getId(), REGION)).thenReturn(false);
        assertThat(check(" ")).isEqualTo(ApiErrorCode.NOT_REGION_MEMBER);
        user.setHomeRegionId(null);
        assertThat(check(" ")).isEqualTo(ApiErrorCode.NO_HOME_REGION);
        user.setCity(" ");
        assertThat(check(" ")).isEqualTo(ApiErrorCode.PROFILE_INCOMPLETE);
        user.setRole(Role.FOODSHARER);
        assertThat(check(" ")).isEqualTo(ApiErrorCode.ROLE_TOO_LOW);
        store.setTeamStatus(TeamSearchStatus.CLOSED);
        assertThat(check(" ")).isEqualTo(ApiErrorCode.TEAM_CLOSED);
        // invitation acceptance skips the team status and the application text
        assertThat(codeOf(() -> service.checkRequirements(store, user, null, false, false)))
                .isEqualTo(ApiErrorCode.ROLE_TOO_LOW);
    }

    @Test
    void requirementsOnlyWhenTheStoreAsks() {
        store.setRequiresVerified(false);
        store.setRequiresPhone(false);
        store.setRequiresHygiene(false);
        store.setRequiresApplicationText(false);
        user.setMobilePhone(null);
        when(quizzes.hygieneValidUntil(user.getId())).thenReturn(Optional.empty());
        assertThat(check(null)).isNull();
    }

    @Test
    void verifiedIsRequiredWhateverTheStoreAsks() {
        store.setRequiresVerified(false);
        user.setVerified(false);
        assertThat(check("x")).isEqualTo(ApiErrorCode.NOT_VERIFIED);
    }

    @Test
    void anUnverifiedApplicantSeesAStoreAsUnknown() {
        user.setVerified(false);
        assertThat(codeOf(() -> service.apply(STORE, "x"))).isEqualTo(ApiErrorCode.STORE_NOT_FOUND);
    }

    @Test
    void unverifiedPeopleCannotBeInvitedAddedOrAccepted() {
        when(rights.rights(any(), any())).thenReturn(editRights());
        when(rights.teamStatus(user.getId(), STORE)).thenReturn(UserTeamStatus.NO_MEMBER);
        when(users.requireActive(user.getId())).thenReturn(user);
        when(users.require(user.getId())).thenReturn(user);
        user.setVerified(false);
        assertThat(codeOf(() -> service.invite(STORE, user.getId()))).isEqualTo(ApiErrorCode.NOT_VERIFIED);
        assertThat(codeOf(() -> service.addDirectly(STORE, user.getId(), false))).isEqualTo(ApiErrorCode.NOT_VERIFIED);
        assertThat(codeOf(() -> service.accept(STORE, user.getId(), false))).isEqualTo(ApiErrorCode.NOT_VERIFIED);
    }

    @Test
    void automationDoesNotAcceptAnUnverifiedApplicant() {
        StoreMembership applied = member(StoreMembershipStatus.APPLIED, false);
        User unverified = new User();
        unverified.setVerified(false);
        when(users.requireActive(applied.getUserId())).thenReturn(unverified);
        assertThat(service.acceptByAutomation(STORE, applied.getUserId(), "request-policy")).isFalse();
        assertThat(applied.getStatus()).isEqualTo(StoreMembershipStatus.APPLIED);
    }

    @Test
    void unverifyingEndsEveryMembershipLikeLeavingWithoutNamingTheStore() {
        StoreMembership manager = member(StoreMembershipStatus.MEMBER, true);
        UUID userId = manager.getUserId();
        when(memberships.findByUserId(userId)).thenReturn(List.of(manager));
        when(stores.liveStores(List.of(STORE))).thenReturn(List.of(store));
        published.clear();

        service.onUserUnverified(new network.foodsharing.user.service.UserUnverifiedEvent(userId));

        verify(memberships).delete(manager);
        verify(pickupInfo).deleteFutureSignups(STORE, userId);
        assertThat(published).filteredOn(e -> e instanceof network.foodsharing.store.domain.StoreTeamChangedEvent)
                .singleElement().isEqualTo(new network.foodsharing.store.domain.StoreTeamChangedEvent(STORE, userId,
                        StoreMembershipStatus.MEMBER, null, false));
        assertThat(published).noneMatch(e -> e instanceof network.foodsharing.notification.service.NotificationRequest);
    }

    @Test
    void applyTwiceIsAlreadyInTeam() {
        when(rights.teamStatus(user.getId(), STORE)).thenReturn(UserTeamStatus.APPLIED);
        assertThat(codeOf(() -> service.apply(STORE, "x"))).isEqualTo(ApiErrorCode.ALREADY_IN_TEAM);
    }

    @Test
    void requestGoesToManagersElseAmbassadorsAfterCommit() {
        User ambassador = new User();
        ambassador.setId(UUID.randomUUID());
        when(regionReads.ambassadorsOf(REGION)).thenReturn(List.of(ambassador));
        service.apply(STORE, "Gerne");
        assertThat(sent(NotificationType.STORE_NEW_REQUEST).recipientIds()).containsExactly(ambassador.getId());

        published.clear();
        UUID manager = member(StoreMembershipStatus.MEMBER, true).getUserId();
        service.apply(STORE, "Gerne");
        assertThat(sent(NotificationType.STORE_NEW_REQUEST).recipientIds()).containsExactly(manager);
    }

    @Test
    void requestFallsBackToOrgaWithoutManagersAndAmbassadors() {
        User orga = new User();
        orga.setId(UUID.randomUUID());
        when(regionReads.ambassadorsOf(REGION)).thenReturn(List.of());
        when(users.findActiveByRole(network.foodsharing.api.model.Role.ORGA)).thenReturn(List.of(orga));
        service.apply(STORE, "Gerne");
        assertThat(sent(NotificationType.STORE_NEW_REQUEST).recipientIds()).containsExactly(orga.getId());
    }

    @Test
    void appointManagerLimitsUnlessCoordinator() {
        member(StoreMembershipStatus.MEMBER, true);
        member(StoreMembershipStatus.MEMBER, true);
        member(StoreMembershipStatus.MEMBER, true);
        StoreMembership candidate = member(StoreMembershipStatus.JUMPER, false);
        User appointee = new User();
        appointee.setId(candidate.getUserId());
        appointee.setRole(Role.FOODSAVER);
        when(users.require(candidate.getUserId())).thenReturn(appointee);

        assertThat(codeOf(() -> service.appointManager(STORE, candidate.getUserId())))
                .isEqualTo(ApiErrorCode.ROLE_TOO_LOW);
        appointee.setRole(Role.STORE_MANAGER);
        assertThat(codeOf(() -> service.appointManager(STORE, candidate.getUserId())))
                .isEqualTo(ApiErrorCode.TOO_MANY_MANAGERS);
        when(rights.mayCoordinateRegionStores(any(), any())).thenReturn(true);
        assertThatCode(() -> service.appointManager(STORE, candidate.getUserId())).doesNotThrowAnyException();
        assertThat(candidate.isManager()).isTrue();
        assertThat(candidate.getStatus()).isEqualTo(StoreMembershipStatus.MEMBER);
        assertThat(codeOf(() -> service.appointManager(STORE, candidate.getUserId())))
                .isEqualTo(ApiErrorCode.ALREADY_MANAGER);
        UUID outsider = UUID.randomUUID();
        when(users.require(outsider)).thenReturn(appointee);
        assertThat(codeOf(() -> service.appointManager(STORE, outsider))).isEqualTo(ApiErrorCode.NOT_TEAM_MEMBER);
    }

    @Test
    void appointBlockerNamesEachReasonInTheOrderAppointManagerChecks() {
        User appointee = new User();
        appointee.setRole(Role.STORE_MANAGER);
        StoreMembership m = new StoreMembership();
        m.setStatus(StoreMembershipStatus.MEMBER);
        assertThat(service.appointBlocker(null, appointee, false, 0)).contains(ApiErrorCode.NOT_TEAM_MEMBER);
        m.setStatus(StoreMembershipStatus.APPLIED);
        assertThat(service.appointBlocker(m, appointee, true, 0)).contains(ApiErrorCode.NOT_TEAM_MEMBER);
        m.setStatus(StoreMembershipStatus.INVITED);
        assertThat(service.appointBlocker(m, appointee, true, 0)).contains(ApiErrorCode.NOT_TEAM_MEMBER);
        m.setStatus(StoreMembershipStatus.JUMPER);
        assertThat(service.appointBlocker(m, appointee, false, 0)).isEmpty();
        m.setStatus(StoreMembershipStatus.MEMBER);
        assertThat(service.appointBlocker(m, appointee, false, 2)).isEmpty();
        assertThat(service.appointBlocker(m, appointee, false, 3)).contains(ApiErrorCode.TOO_MANY_MANAGERS);
        assertThat(service.appointBlocker(m, appointee, true, 3)).isEmpty();
        appointee.setRole(Role.FOODSAVER);
        assertThat(service.appointBlocker(m, appointee, false, 3)).contains(ApiErrorCode.ROLE_TOO_LOW);
        assertThat(service.appointBlocker(m, appointee, true, 0)).contains(ApiErrorCode.ROLE_TOO_LOW);
        m.setManager(true);
        assertThat(service.appointBlocker(m, appointee, true, 0)).contains(ApiErrorCode.ALREADY_MANAGER);
    }

    @Test
    void teamListCarriesTheAppointBlockerOnlyForTeamEditors() {
        teamFixture(editRights());
        people.values().forEach(u -> u.setRole(Role.FOODSAVER));
        people.get(UUID.fromString("00000000-0000-4000-8000-000000000013")).setRole(Role.STORE_MANAGER);
        var rows = service.teamMembers(STORE, new TeamListQuery(null, null, null, 0, 20)).getContent();
        assertThat(rows).extracting(v -> v.user().getDisplayName() + ":" + v.appointBlocker() + ":" + v.mayBeAppointed())
                .containsExactly("Zora:ALREADY_MANAGER:false", "anna:ROLE_TOO_LOW:false", "Anna:null:true",
                        "Bert:ROLE_TOO_LOW:false", "Émile:ROLE_TOO_LOW:false");
        when(stores.requireAccess(store)).thenReturn(noRights());
        assertThat(service.teamMembers(STORE, new TeamListQuery(null, null, null, 0, 20)).getContent())
                .allSatisfy(v -> {
                    assertThat(v.appointBlocker()).isNull();
                    assertThat(v.mayBeAppointed()).isFalse();
                });
    }

    @Test
    void inviteBlockerNamesEachReasonInTheOrderInviteChecks() {
        assertThat(service.inviteBlocker(STORE, user)).isEmpty();
        user.setVerified(false);
        assertThat(service.inviteBlocker(STORE, user)).contains(ApiErrorCode.NOT_VERIFIED);
        user.setRole(Role.FOODSHARER);
        assertThat(service.inviteBlocker(STORE, user)).contains(ApiErrorCode.ROLE_TOO_LOW);
        for (UserTeamStatus status : List.of(UserTeamStatus.APPLIED, UserTeamStatus.INVITED, UserTeamStatus.MEMBER,
                UserTeamStatus.WAITING_LIST, UserTeamStatus.COORDINATOR)) {
            when(rights.teamStatus(user.getId(), STORE)).thenReturn(status);
            assertThat(service.inviteBlocker(STORE, user)).as(status.name()).contains(ApiErrorCode.ALREADY_IN_TEAM);
        }
    }

    private RegionMembersService.MemberView regionMember(String name, Role role, boolean verified) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setDisplayName(name);
        u.setRole(role);
        u.setVerified(verified);
        return new RegionMembersService.MemberView(u, null, false, java.util.Set.of(), true, false);
    }

    @Test
    void inviteCandidatesAreRegionMembersInviteWouldAccept() {
        RegionMembersService.MemberView ok = regionMember("Sarah", Role.FOODSAVER, true);
        RegionMembersService.MemberView sharer = regionMember("Lea", Role.FOODSHARER, true);
        RegionMembersService.MemberView unverified = regionMember("Chiara", Role.FOODSAVER, false);
        RegionMembersService.MemberView inTeam = regionMember("Nina", Role.STORE_MANAGER, true);
        when(rights.teamStatus(inTeam.user().getId(), STORE)).thenReturn(UserTeamStatus.COORDINATOR);
        when(regionMembers.members(REGION, "a", true, null, null, 0, 10)).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(sharer, unverified, inTeam, ok),
                        org.springframework.data.domain.PageRequest.of(0, 10), 4));
        assertThat(service.inviteCandidates(STORE, "a", 10)).extracting(User::getDisplayName).containsExactly("Sarah");
        when(rights.rights(any(), any())).thenReturn(noRights());
        assertThat(codeOf(() -> service.inviteCandidates(STORE, "a", 10))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void inviteCandidatesReadABoundedNumberOfPages() {
        RegionMembersService.MemberView sharer = regionMember("Lea", Role.FOODSHARER, true);
        when(regionMembers.members(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> new org.springframework.data.domain.PageImpl<>(
                List.of(sharer), org.springframework.data.domain.PageRequest.of(i.getArgument(5), 1), 1000));
        assertThat(service.inviteCandidates(STORE, "le", 1)).isEmpty();
        verify(regionMembers, org.mockito.Mockito.times(StoreTeamService.CANDIDATE_PAGES)).members(any(), any(),
                any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void lastManagerStaysUnlessCoordinator() {
        StoreMembership only = member(StoreMembershipStatus.MEMBER, true);
        assertThat(codeOf(() -> service.removeManager(STORE, only.getUserId(), "Danke")))
                .isEqualTo(ApiErrorCode.LAST_MANAGER);
        assertThat(codeOf(() -> service.removeManager(STORE, only.getUserId(), " ")))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        UUID plain = member(StoreMembershipStatus.MEMBER, false).getUserId();
        assertThat(codeOf(() -> service.removeManager(STORE, plain, "x"))).isEqualTo(ApiErrorCode.NOT_MANAGER);
        when(rights.mayCoordinateRegionStores(any(), any())).thenReturn(true);
        service.removeManager(STORE, only.getUserId(), "Danke");
        assertThat(only.isManager()).isFalse();
        assertThat(sent(NotificationType.STORE_MANAGER_REMOVED).params()).containsEntry("message", "Danke");
    }

    @Test
    void managerCannotLeaveAndLeavingDeletesFutureSignups() {
        StoreMembership self = member(StoreMembershipStatus.MEMBER, true);
        user.setId(self.getUserId());
        assertThat(codeOf(() -> service.remove(STORE, self.getUserId(), null)))
                .isEqualTo(ApiErrorCode.MANAGER_CANNOT_LEAVE);
        self.setManager(false);
        service.remove(STORE, self.getUserId(), null);
        verify(pickupInfo).deleteFutureSignups(STORE, self.getUserId());
        verify(memberships).delete(self);
        assertThat(published).noneMatch(StoreNotifications.Send.class::isInstance); // leaving sends no bell
        assertThat(published).allMatch(network.foodsharing.store.domain.StoreTeamChangedEvent.class::isInstance);
    }

    @Test
    void removingSomeoneElseNeedsMessageAndEditRights() {
        UUID other = member(StoreMembershipStatus.MEMBER, false).getUserId();
        assertThat(codeOf(() -> service.remove(STORE, other, ""))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(pickupInfo, never()).deleteFutureSignups(any(), any());
        service.remove(STORE, other, "Tschüss");
        assertThat(sent(NotificationType.STORE_MEMBER_REMOVED).mailTemplate()).isEqualTo("store/member-removed");
        when(rights.rights(any(), any())).thenReturn(noRights());
        UUID another = member(StoreMembershipStatus.MEMBER, false).getUserId();
        assertThatThrownBy(() -> service.remove(STORE, another, "x")).isInstanceOf(ApiException.class);
    }

    @Test
    void teamChangesPublishEventsAndRequiredMessagesGoToTheChat() {
        when(currentActor.currentUserId()).thenReturn(Optional.of(user.getId()));
        StoreMembership m = member(StoreMembershipStatus.MEMBER, false);
        service.moveToStandby(STORE, m.getUserId(), "Bitte pausieren");
        assertThat(published).filteredOn(network.foodsharing.store.domain.StoreTeamChangedEvent.class::isInstance)
                .containsExactly(new network.foodsharing.store.domain.StoreTeamChangedEvent(STORE, m.getUserId(),
                        StoreMembershipStatus.MEMBER, StoreMembershipStatus.JUMPER, false));
        verify(teamChat).sendDirect(user.getId(), m.getUserId(), "Bitte pausieren");
        service.moveToTeam(STORE, m.getUserId());
        service.remove(STORE, m.getUserId(), "Tschüss");
        assertThat(published).filteredOn(network.foodsharing.store.domain.StoreTeamChangedEvent.class::isInstance)
                .extracting("newStatus").containsExactly(StoreMembershipStatus.JUMPER, StoreMembershipStatus.MEMBER, null);
        verify(teamChat).sendDirect(user.getId(), m.getUserId(), "Tschüss");
    }

    @Test
    void distanceIsHaversineInKilometres() {
        User u = new User();
        u.setLatitude(47.3779);
        u.setLongitude(8.5403);
        Store s = new Store();
        s.setLatitude(47.4997);
        s.setLongitude(8.7241);
        assertThat(StoreTeamService.distance(u, s)).isBetween(18.5, 19.5);
        s.setLatitude(null);
        assertThat(StoreTeamService.distance(u, s)).isNull();
    }

    @Test
    void automatedRequestDecisionsAreSentOnBehalfOfTheStoresRegion() {
        StoreMembership accepted = member(StoreMembershipStatus.APPLIED, false);
        StoreMembership declined = member(StoreMembershipStatus.APPLIED, false);
        User verifiedApplicant = new User();
        verifiedApplicant.setVerified(true);
        when(users.requireActive(accepted.getUserId())).thenReturn(verifiedApplicant);

        assertThat(service.acceptByAutomation(STORE, accepted.getUserId(), "request-policy")).isTrue();
        assertThat(service.declineByAutomation(STORE, declined.getUserId(), "request-policy", " Team voll ")).isTrue();

        var accept = sent(NotificationType.STORE_REQUEST_AUTO_ACCEPTED);
        assertThat(accept.senderRegionId()).isEqualTo(REGION);
        assertThat(accept.recipientIds()).containsExactly(accepted.getUserId());
        var decline = sent(NotificationType.STORE_REQUEST_AUTO_DECLINED);
        assertThat(decline.senderRegionId()).isEqualTo(REGION);
        assertThat(decline.mailTemplate()).isEqualTo("store/request-auto-declined");
        assertThat(decline.params()).containsEntry("message", "Team voll");

        // decisions of a person are not region mails
        StoreMembership manual = member(StoreMembershipStatus.APPLIED, false);
        when(users.require(manual.getUserId())).thenReturn(verifiedApplicant);
        service.accept(STORE, manual.getUserId(), false);
        assertThat(sent(NotificationType.STORE_REQUEST_ACCEPTED).senderRegionId()).isNull();
    }

    // ------------------------------------------------------------------------- team list (resume item 14)

    private final java.util.Map<UUID, User> people = new java.util.LinkedHashMap<>();
    private final java.util.Map<UUID, StorePickupInfo.MemberStats> stats = new java.util.HashMap<>();

    /** A team member with a fixed id (for the id tiebreaker), pickups and last pickup (null = none). */
    private StoreMembership person(String id, String name, StoreMembershipStatus status, boolean manager, int pickups,
                                   String lastPickup) {
        User u = new User();
        u.setId(UUID.fromString("00000000-0000-4000-8000-0000000000" + id));
        u.setDisplayName(name);
        u.setVerified(true);
        u.setMobilePhone("+4179000000" + id);
        u.setLatitude(47.38);
        u.setLongitude(8.54);
        people.put(u.getId(), u);
        stats.put(u.getId(), new StorePickupInfo.MemberStats(pickups, null,
                lastPickup == null ? null : Instant.parse(lastPickup)));
        StoreMembership m = new StoreMembership();
        m.setStoreId(STORE);
        m.setUserId(u.getId());
        m.setStatus(status);
        m.setManager(manager);
        team.add(m);
        return m;
    }

    private void teamFixture(StorePermissionService.Rights r) {
        store.setLatitude(47.37);
        store.setLongitude(8.54);
        when(stores.requireAccess(store)).thenReturn(r);
        when(users.findAll(any())).thenAnswer(i -> new ArrayList<>(people.values()));
        when(pickupInfo.memberStats(STORE)).thenReturn(stats);
        person("11", "Zora", StoreMembershipStatus.MEMBER, true, 3, "2026-10-01T08:00:00Z");
        person("12", "anna", StoreMembershipStatus.MEMBER, false, 5, "2026-09-01T08:00:00Z");
        person("13", "Anna", StoreMembershipStatus.MEMBER, false, 3, "2026-10-20T08:00:00Z");
        person("14", "Émile", StoreMembershipStatus.JUMPER, false, 0, null);
        person("15", "Bert", StoreMembershipStatus.JUMPER, false, 5, "2026-10-01T08:00:00Z");
        person("16", "Applicant", StoreMembershipStatus.APPLIED, false, 0, null);
        person("17", "Invited", StoreMembershipStatus.INVITED, false, 0, null);
    }

    private List<String> names(TeamListQuery.Status status, String search, TeamListQuery.Sort sort) {
        return service.teamMembers(STORE, new TeamListQuery(status, search, sort, 0, 20)).getContent().stream()
                .map(v -> v.user().getDisplayName() + (v.user().getId().toString().endsWith("13") ? "#13" : ""))
                .toList();
    }

    @Test
    void teamListDefaultOrderIsManagersFirstThenNameThenId() {
        teamFixture(editRights());
        assertThat(names(null, null, null)).containsExactly("Zora", "anna", "Anna#13", "Bert", "Émile");
    }

    @Test
    void teamListSortsByPickupsAndLastPickupWithNoPickupLast() {
        teamFixture(editRights());
        // ties: 5 (anna, Bert) and 3 (Anna, Zora) by name then id; the manager loses the top
        assertThat(names(null, null, TeamListQuery.Sort.PICKUP_COUNT))
                .containsExactly("anna", "Bert", "Anna#13", "Zora", "Émile");
        // ties on 2026-10-01 (Bert, Zora) by name; no pickup last
        assertThat(names(null, null, TeamListQuery.Sort.LAST_PICKUP))
                .containsExactly("Anna#13", "Bert", "Zora", "anna", "Émile");
    }

    @Test
    void teamListFiltersByStatusAndSearch() {
        teamFixture(editRights());
        assertThat(names(TeamListQuery.Status.MANAGER, null, null)).containsExactly("Zora");
        assertThat(names(TeamListQuery.Status.MEMBER, null, null)).containsExactly("Zora", "anna", "Anna#13");
        assertThat(names(TeamListQuery.Status.JUMPER, null, null)).containsExactly("Bert", "Émile");
        // case- and accent-insensitive, applicants and invitations never listed
        assertThat(names(null, "EMI", null)).containsExactly("Émile");
        assertThat(names(null, " ANNA ", null)).containsExactly("anna", "Anna#13");
        assertThat(names(null, "applicant", null)).isEmpty();
    }

    @Test
    void teamListPagesWithinBounds() {
        teamFixture(editRights());
        var second = service.teamMembers(STORE, new TeamListQuery(null, null, null, 1, 2));
        assertThat(second.getContent()).extracting(v -> v.user().getDisplayName()).containsExactly("Anna", "Bert");
        assertThat(second.getTotalElements()).isEqualTo(5);
        assertThat(second.getTotalPages()).isEqualTo(3);
        assertThat(service.teamMembers(STORE, new TeamListQuery(null, null, null, 9, 2)).getContent()).isEmpty();
        assertThat(new TeamListQuery(null, null, null, -1, 500).size()).isEqualTo(TeamListQuery.MAX_SIZE);
    }

    @Test
    void teamListKeepsDistanceAndPhoneVisibility() {
        teamFixture(editRights());
        var withRights = service.teamMembers(STORE, new TeamListQuery(null, null, null, 0, 20)).getContent().getFirst();
        assertThat(withRights.distanceKm()).isEqualTo(1.1);
        assertThat(withRights.withPhone()).isTrue();
        when(stores.requireAccess(store)).thenReturn(noRights());
        var without = service.teamMembers(STORE, new TeamListQuery(null, null, null, 0, 20)).getContent().getFirst();
        assertThat(without.distanceKm()).isNull();
        assertThat(without.withPhone()).isFalse();
        assertThat(service.team(STORE).applications()).isNull();
    }

    @Test
    void teamViewHasOnlyApplicationsAndInvitations() {
        teamFixture(editRights());
        var view = service.team(STORE);
        assertThat(view.applications()).extracting(v -> v.user().getDisplayName()).containsExactly("Applicant");
        assertThat(view.invitations()).extracting(v -> v.user().getDisplayName()).containsExactly("Invited");
    }

    private StoreMembership member(StoreMembershipStatus status, boolean manager) {
        StoreMembership m = new StoreMembership();
        m.setStoreId(STORE);
        m.setUserId(UUID.randomUUID());
        m.setStatus(status);
        m.setManager(manager);
        team.add(m);
        when(memberships.findByStoreIdAndUserId(STORE, m.getUserId())).thenReturn(Optional.of(m));
        return m;
    }

    private network.foodsharing.notification.service.NotificationRequest sent(NotificationType type) {
        return published.stream().filter(StoreNotifications.Send.class::isInstance)
                .map(e -> ((StoreNotifications.Send) e).request()).filter(r -> r.type() == type).findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " in " + published));
    }

    private static StorePermissionService.Rights editRights() {
        return new StorePermissionService.Rights(true, true, true, false, true, true, true, true, true, true, true,
                true, true, false, false, false, true, true, false, true, false, UserTeamStatus.COORDINATOR);
    }

    private static StorePermissionService.Rights noRights() {
        return new StorePermissionService.Rights(true, false, false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false, false, false, false, UserTeamStatus.MEMBER);
    }
}
