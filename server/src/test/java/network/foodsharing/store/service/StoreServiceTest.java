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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.domain.WallMilestone;
import network.foodsharing.store.repository.GroceryRepository;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreGroceryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Store update rules (B2): 404 / deleted, edit right, region move guard, milestones, reopen event, log actions. */
class StoreServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.fromString("00000020-0001-4000-8000-000000000001");
    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID TARGET = UUID.fromString("00000000-0000-4000-8000-000000000003");

    StoreRepository stores = mock(StoreRepository.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    RegionService regions = mock(RegionService.class);
    RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
    UserService users = mock(UserService.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    List<Object> published = new ArrayList<>();
    StorePickupInfo pickupInfo = mock(StorePickupInfo.class);
    StoreWallService wall = mock(StoreWallService.class);
    StoreService service;
    Store store;

    @BeforeEach
    void setUp() {
        service = new StoreService(stores, memberships, mock(StoreCategoryRepository.class),
                mock(StoreChainRepository.class), mock(GroceryRepository.class), mock(StoreGroceryRepository.class),
                mock(PickupWeightCategoryRepository.class), rights, regions, resolver, users, currentActor, history,
                published::add, new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC), provider(pickupInfo),
                provider(wall));
        store = new Store();
        store.setId(STORE);
        store.setName("Coop");
        store.setRegionId(REGION);
        store.setTeamStatus(TeamSearchStatus.CLOSED);
        store.setCooperationStatus(CooperationStatus.IN_NEGOTIATION);
        store.setPrefetchDays(14);
        when(stores.findById(STORE)).thenReturn(Optional.of(store));
        when(currentActor.requirePrincipal())
                .thenReturn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.STORE_MANAGER, "a@b.c"));
        when(rights.rights(any(), any())).thenReturn(rights(true));
        Region target = new Region();
        target.setId(TARGET);
        when(regions.require(TARGET)).thenReturn(target);
    }

    static ApiErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return (ApiErrorCode) ReflectionTestUtils.getField(e, "code");
        }
        return null;
    }

    @Test
    void requireDistinguishesMissingAndDeleted() {
        assertThat(codeOf(() -> service.require(UUID.randomUUID()))).isEqualTo(ApiErrorCode.STORE_NOT_FOUND);
        store.setDeletedAt(NOW);
        assertThat(codeOf(() -> service.require(STORE))).isEqualTo(ApiErrorCode.STORE_DELETED);
    }

    @Test
    void updateNeedsEditRight() {
        when(rights.rights(any(), any())).thenReturn(rights(false));
        assertThat(codeOf(() -> service.update(STORE, input(Map.of("name", "X"))))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(store.getName()).isEqualTo("Coop");
    }

    @Test
    void prefetchDaysBetweenOneAndFortyTwo() {
        assertThat(codeOf(() -> service.update(STORE, input(Map.of("prefetchDays", 0)))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(() -> service.update(STORE, input(Map.of("prefetchDays", 43)))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        service.update(STORE, input(Map.of("prefetchDays", 42)));
        assertThat(store.getPrefetchDays()).isEqualTo(42);
        verify(history).event(StoreLogAction.UPDATE_REGULAR_PICKUP_INTERVAL, STORE);
    }

    @Test
    void moveNeedsWholeTeamInTargetRegion() {
        User a = user("Zora");
        User b = user("anna");
        when(memberships.findByStoreId(STORE)).thenReturn(List.of(membership(a), membership(b)));
        when(users.findAll(any())).thenReturn(List.of(a, b));
        when(resolver.isMember(a.getId(), TARGET)).thenReturn(false);
        when(resolver.isMember(b.getId(), TARGET)).thenReturn(false);
        try {
            service.update(STORE, input(Map.of("regionId", TARGET)));
            throw new AssertionError("expected 409");
        } catch (ApiException e) {
            assertThat(ReflectionTestUtils.getField(e, "code")).isEqualTo(ApiErrorCode.TEAM_MEMBERS_NOT_IN_TARGET_REGION);
            assertThat(e.getArgs()).containsEntry("names", "anna, Zora");
        }
        assertThat(store.getRegionId()).isEqualTo(REGION);

        when(resolver.isMember(any(), any())).thenReturn(true);
        service.update(STORE, input(Map.of("regionId", TARGET)));
        assertThat(store.getRegionId()).isEqualTo(TARGET);
        verify(history).event(StoreLogAction.MOVED_TO_REGION, STORE);
    }

    @Test
    void cooperationStartedMilestoneAndStatusDate() {
        service.update(STORE, input(Map.of("cooperationStatus", CooperationStatus.COOPERATION_ESTABLISHED)));
        assertThat(store.getStatusDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(milestones()).containsExactly(WallMilestone.COOPERATION_STARTED);
        verify(history).event(StoreLogAction.COOPERATION_STATUS_CHANGED, STORE);

        published.clear();
        service.update(STORE, input(Map.of("cooperationStatus", CooperationStatus.PERMANENTLY_CLOSED)));
        assertThat(milestones()).containsExactly(WallMilestone.STATUS_CHANGED);
    }

    @Test
    void reopeningTheTeamNotifiesOnlyFromClosed() {
        service.update(STORE, input(Map.of("teamStatus", TeamSearchStatus.OPEN)));
        assertThat(milestones()).containsExactly(WallMilestone.TEAM_STATUS_CHANGED);
        assertThat(published).hasAtLeastOneElementOfType(StoreService.TeamReopened.class);
        verify(history).event(StoreLogAction.TEAM_STATUS_CHANGED, STORE);

        published.clear();
        service.update(STORE, input(Map.of("teamStatus", TeamSearchStatus.OPEN_SEARCHING)));
        assertThat(published).noneMatch(StoreService.TeamReopened.class::isInstance);
    }

    @Test
    void unchangedFieldsLogOnlyTheUpdate() {
        service.update(STORE, input(Map.of("name", "Coop City")));
        assertThat(store.getName()).isEqualTo("Coop City");
        assertThat(published).isEmpty();
        verify(history).event(StoreLogAction.STORE_UPDATED, STORE);
        verify(history, never()).event(StoreLogAction.TEAM_STATUS_CHANGED, STORE);
        assertThat(codeOf(() -> service.update(STORE, input(Map.of("name", " "))))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void regionPickupRuleToggleIsLogged() {
        service.update(STORE, input(Map.of("useRegionPickupRule", true)));
        verify(history).event(StoreLogAction.REGION_PICKUP_RULE_ENABLED, STORE);
        service.update(STORE, input(Map.of("useRegionPickupRule", false)));
        verify(history).event(StoreLogAction.REGION_PICKUP_RULE_DISABLED, STORE);
    }

    private List<WallMilestone> milestones() {
        return published.stream().filter(StoreService.StoreChanged.class::isInstance)
                .map(e -> ((StoreService.StoreChanged) e).milestone()).toList();
    }

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        return user;
    }

    private static StoreMembership membership(User user) {
        StoreMembership m = new StoreMembership();
        m.setUserId(user.getId());
        m.setStoreId(STORE);
        m.setStatus(StoreMembershipStatus.MEMBER);
        return m;
    }

    private static StoreService.StoreInput input(Map<String, Object> v) {
        return new StoreService.StoreInput((String) v.get("name"), null, null, null, null, null, null, null, null,
                null, (CooperationStatus) v.get("cooperationStatus"), null, null, null, null, null, null, null, null,
                null, null, null, (Integer) v.get("prefetchDays"), (Boolean) v.get("useRegionPickupRule"), null, null,
                null, null, (TeamSearchStatus) v.get("teamStatus"), null, null, (UUID) v.get("regionId"), null, null);
    }

    @SuppressWarnings("unchecked")
    static <T> org.springframework.beans.factory.ObjectProvider<T> provider(T bean) {
        org.springframework.beans.factory.ObjectProvider<T> p = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(p.getObject()).thenReturn(bean);
        return p;
    }

    static StorePermissionService.Rights deleteRights(boolean mayDelete) {
        return new StorePermissionService.Rights(true, true, true, false, true, true, true, true, true, true, true,
                true, true, mayDelete, false, true, true, false, false, false, mayDelete, UserTeamStatus.NO_MEMBER);
    }

    @Test
    void deleteNeedsCoordination() {
        when(rights.rights(any(), any())).thenReturn(deleteRights(false));
        assertThat(codeOf(() -> service.delete(STORE, null))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(store.getDeletedAt()).isNull();
    }

    @Test
    void deleteRefusedWhenTheStoreEverHadSignups() {
        when(rights.rights(any(), any())).thenReturn(deleteRights(true));
        when(pickupInfo.hasSignups(STORE)).thenReturn(true);
        assertThat(codeOf(() -> service.delete(STORE, null))).isEqualTo(ApiErrorCode.STORE_HAS_PICKUPS);
        assertThat(store.getDeletedAt()).isNull();
        verify(wall, never()).deleteAllOf(any(), any());
    }

    @Test
    void deleteArchivesStoreLogsAndNotifiesTeamJumpersApplicants() {
        when(rights.rights(any(), any())).thenReturn(deleteRights(true));
        User member = user("m");
        User jumper = user("j");
        User applicant = user("a");
        User invited = user("i");
        StoreMembership mj = membership(jumper);
        mj.setStatus(StoreMembershipStatus.JUMPER);
        StoreMembership ma = membership(applicant);
        ma.setStatus(StoreMembershipStatus.APPLIED);
        StoreMembership mi = membership(invited);
        mi.setStatus(StoreMembershipStatus.INVITED);
        when(memberships.findByStoreId(STORE)).thenReturn(List.of(membership(member), mj, ma, mi));
        service.delete(STORE, " closed ");
        assertThat(store.getDeletedAt()).isEqualTo(NOW);
        verify(wall).deleteAllOf(any(), any());
        verify(history).event(StoreLogAction.DELETE_STORE, STORE);
        var send = published.stream().filter(StoreNotifications.Send.class::isInstance)
                .map(StoreNotifications.Send.class::cast).findFirst().orElseThrow();
        assertThat(send.request().type()).isEqualTo(network.foodsharing.notification.domain.NotificationType.STORE_DELETED);
        assertThat(send.request().recipientIds()).containsExactlyInAnyOrder(member.getId(), jumper.getId(),
                applicant.getId());
    }

    static StorePermissionService.Rights rights(boolean edit) {
        return new StorePermissionService.Rights(true, true, true, false, edit, edit, true, edit, true, edit, edit,
                edit, edit, false, false, true, true, false, false, edit, false,
                edit ? UserTeamStatus.COORDINATOR : UserTeamStatus.MEMBER);
    }
}
