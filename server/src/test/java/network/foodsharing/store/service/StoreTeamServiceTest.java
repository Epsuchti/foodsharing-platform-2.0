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
                resolver, regionReads, events, currentActor, history, properties, Clock.fixed(NOW, ZoneOffset.UTC), teamChat);
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
        user.setVerified(false);
        user.setMobilePhone(null);
        when(quizzes.hygieneValidUntil(user.getId())).thenReturn(Optional.empty());
        assertThat(check(null)).isNull();
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
