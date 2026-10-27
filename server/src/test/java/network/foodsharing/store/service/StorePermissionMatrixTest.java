package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The six upstream permission tables of {@code docs/en/permissions/store.md} (Wall, Team list, Pickup list, Options,
 * Store information modal, Information) as rows {@code actor × status}, one column per right (A19). Where the
 * upstream tables contradict upstream {@code StorePermissions} code, the code wins (documented per row): unverified
 * team members read the wall; jumper chat is managers and jumpers only. The "delete older than a month" wall column
 * is region-dependent and lives in {@code StoreWallServiceTest}.
 *
 * <p>Columns: access, readWall, postWall, deleteAnyWallPost, editTeam, phones, seePickups, editPickups,
 * confirmPickups, chatTeam, chatJumper, editStore, delete (coordinate).
 */
class StorePermissionMatrixTest {

    static final UUID PARENT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID REGION = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID COORDINATION = UUID.fromString("00000000-0000-4000-8000-000000000003");
    static final UUID OTHER_COORDINATION = UUID.fromString("00000000-0000-4000-8000-000000000004");
    static final UUID CHAIN = UUID.fromString("00000000-0000-4000-8000-000000000005");

    enum Scope { NONE, IN_DISTRICT, OUT_OF_DISTRICT }

    record Row(String name, Role role, Scope ambassador, Scope coordinationAdmin, boolean coordinationGroupExists,
               StoreMembershipStatus status, boolean manager, boolean verified, boolean kam, String expected) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Row r(String name, Role role, Scope amb, Scope coord, boolean group, StoreMembershipStatus status,
                 boolean manager, boolean verified, boolean kam, String expected) {
        return new Row(name, role, amb, coord, group, status, manager, verified, kam, expected.replace(" ", ""));
    }

    static final StoreMembershipStatus NONE = null;
    static final StoreMembershipStatus MEMBER = StoreMembershipStatus.MEMBER;
    static final StoreMembershipStatus JUMPER = StoreMembershipStatus.JUMPER;

    static Stream<Arguments> rows() {
        //                                                                         acc wal pst del tea pho see edp cnf chT chJ edS dlt
        return Stream.of(
                r("orga, not in team", Role.ORGA, Scope.NONE, Scope.NONE, false, NONE, false, true, false,
                        "y y y y y y y y y n n y y"),
                r("orga, in team", Role.ORGA, Scope.NONE, Scope.NONE, false, MEMBER, false, true, false,
                        "y y y y y y y y y y n y y"),
                r("ambassador in district, not in team", Role.AMBASSADOR, Scope.IN_DISTRICT, Scope.NONE, false, NONE,
                        false, true, false, "y y y n y y y y y n n y y"),
                r("ambassador in district, in team", Role.AMBASSADOR, Scope.IN_DISTRICT, Scope.NONE, false, MEMBER,
                        false, true, false, "y y y n y y y y y y n y y"),
                r("ambassador out of district, not in team", Role.AMBASSADOR, Scope.OUT_OF_DISTRICT, Scope.NONE,
                        false, NONE, false, true, false, "n n n n n n n n n n n n n"),
                // upstream table says jumper chat "yes"; upstream code (verantwortlich || jumper) says no
                r("ambassador out of district, in team", Role.AMBASSADOR, Scope.OUT_OF_DISTRICT, Scope.NONE, false,
                        MEMBER, false, true, false, "y y y n n y y n n y n n n"),
                r("coordination admin in district, not in team", Role.FOODSAVER, Scope.NONE, Scope.IN_DISTRICT, true,
                        NONE, false, true, false, "y y y n n y y n n n n n y"),
                r("coordination admin (store manager role) in district, not in team", Role.STORE_MANAGER, Scope.NONE,
                        Scope.IN_DISTRICT, true, NONE, false, true, false, "y y y n y y y y y n n y y"),
                r("coordination admin in district, in team", Role.STORE_MANAGER, Scope.NONE, Scope.IN_DISTRICT, true,
                        MEMBER, false, true, false, "y y y n y y y y y y n y y"),
                r("coordination admin out of district, not in team", Role.STORE_MANAGER, Scope.NONE,
                        Scope.OUT_OF_DISTRICT, true, NONE, false, true, false, "n n n n n n n n n n n n n"),
                r("coordination admin out of district, in team", Role.STORE_MANAGER, Scope.NONE,
                        Scope.OUT_OF_DISTRICT, true, MEMBER, false, true, false, "y y y n n y y n n y n n n"),
                // store coordination supersedes ambassadors: the region has a coordination group
                r("ambassador in district superseded by coordination group", Role.AMBASSADOR, Scope.IN_DISTRICT,
                        Scope.NONE, true, NONE, false, true, false, "n n n n n n n n n n n n n"),
                r("store manager", Role.STORE_MANAGER, Scope.NONE, Scope.NONE, false, MEMBER, true, true, false,
                        "y y y n y y y y y y y y n"),
                r("foodsaver, active member", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, MEMBER, false, true,
                        false, "y y y n n y y n n y n n n"),
                r("foodsaver, jumper", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, JUMPER, false, true, false,
                        "y n n n n n n n n n y n n"),
                // upstream table: wall "no"; upstream code mayReadStoreWall: team members read the wall
                r("foodsaver, unverified member", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, MEMBER, false, false,
                        false, "y y y n n n n n n y n n n"),
                r("foodsaver, unverified jumper", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, JUMPER, false, false,
                        false, "y n n n n n n n n n y n n"),
                r("foodsaver, applied", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, StoreMembershipStatus.APPLIED,
                        false, true, false, "n n n n n n n n n n n n n"),
                r("foodsaver, invited", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, StoreMembershipStatus.INVITED,
                        false, true, false, "n n n n n n n n n n n n n"),
                r("key account manager of the chain", Role.STORE_MANAGER, Scope.NONE, Scope.NONE, false, NONE, false,
                        true, true, "y n n n n n y n n n n n n"),
                r("key account manager with foodsaver role", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, NONE,
                        false, true, true, "n n n n n n n n n n n n n"))
                .map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void matrix(Row row) {
        UUID userId = UUID.randomUUID();
        Store store = store(TeamSearchStatus.CLOSED);
        StorePermissionService service = service(row, userId);
        SessionPrincipal actor = new SessionPrincipal(userId, UUID.randomUUID(), row.role(), "x@example.org");

        StorePermissionService.Rights rights = service.rights(actor, store);

        List<Boolean> actual = List.of(rights.mayAccess(), rights.mayReadWall(), rights.mayPostWall(),
                rights.mayDeleteAnyWallPost(), rights.mayEditTeam(), rights.maySeePhoneNumbers(),
                rights.maySeePickups(), rights.mayEditPickups(), rights.mayConfirmPickups(), rights.mayChatTeam(),
                rights.mayChatJumper(), rights.mayEditStore(), rights.mayDelete());
        List<Boolean> expected = new ArrayList<>();
        for (char c : row.expected().toCharArray()) {
            expected.add(c == 'y');
        }
        assertThat(actual).as(row.name()).isEqualTo(expected);
        assertThat(rights.maySeeStoreLog()).isEqualTo(rights.mayEditStore());
        assertThat(rights.maySeeMemberDistance()).isEqualTo(rights.mayEditStore());
        assertThat(rights.maySeePickupHistory()).isEqualTo(rights.mayEditStore() || rights.isKam());
        assertThat(rights.isManager()).isEqualTo(row.manager());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void applyOnlyWithoutMembershipAndOpenTeam(Row row) {
        UUID userId = UUID.randomUUID();
        StorePermissionService service = service(row, userId);
        SessionPrincipal actor = new SessionPrincipal(userId, UUID.randomUUID(), row.role(), "x@example.org");

        assertThat(service.rights(actor, store(TeamSearchStatus.CLOSED)).mayApply()).isFalse();
        assertThat(service.rights(actor, store(TeamSearchStatus.OPEN)).mayApply()).isEqualTo(row.status() == null);
        assertThat(service.rights(actor, store(TeamSearchStatus.OPEN)).mayLeave())
                .isEqualTo(row.status() != null && row.status() != StoreMembershipStatus.INVITED && !row.manager());
    }

    @org.junit.jupiter.api.Test
    void anonymousHasNoRights() {
        Row row = rows().map(a -> (Row) a.get()[0]).findFirst().orElseThrow();
        StorePermissionService service = service(row, UUID.randomUUID());
        StorePermissionService.Rights rights = service.rights(null, store(TeamSearchStatus.OPEN));
        assertThat(rights.mayAccess()).isFalse();
        assertThat(rights.mayApply()).isFalse();
        assertThat(service.mayCoordinateRegionStores(null, REGION)).isFalse();
        assertThat(service.mayCreateStore(null, REGION)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void createStoreNeedsStoreManagerRoleAndRegionMembership() {
        UUID userId = UUID.randomUUID();
        Row row = r("x", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, NONE, false, true, false, "");
        RegionPermissionResolver resolver = mock(RegionPermissionResolver.class);
        when(resolver.isMember(userId, REGION)).thenReturn(true);
        StorePermissionService service = service(row, userId, resolver);
        assertThat(service.mayCreateStore(principal(userId, Role.FOODSAVER), REGION)).isFalse();
        assertThat(service.mayCreateStore(principal(userId, Role.STORE_MANAGER), REGION)).isTrue();
        assertThat(service.mayCreateStore(principal(userId, Role.STORE_MANAGER), PARENT)).isFalse();
        assertThat(service.mayCreateStore(principal(UUID.randomUUID(), Role.ORGA), PARENT)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void removePickupUserSelfEditorOrUserAdministrator() {
        UUID userId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Row row = r("x", Role.FOODSAVER, Scope.NONE, Scope.NONE, false, MEMBER, false, true, false, "");
        StorePermissionService service = service(row, userId);
        Store store = store(TeamSearchStatus.CLOSED);
        assertThat(service.mayRemovePickupUser(principal(userId, Role.FOODSAVER), store, userId)).isTrue();
        assertThat(service.mayRemovePickupUser(principal(userId, Role.FOODSAVER), store, other)).isFalse();
        assertThat(service.mayRemovePickupUser(principal(UUID.randomUUID(), Role.ORGA), store, other)).isTrue();
    }

    private static SessionPrincipal principal(UUID userId, Role role) {
        return new SessionPrincipal(userId, UUID.randomUUID(), role, "x@example.org");
    }

    private static Store store(TeamSearchStatus teamStatus) {
        Store store = new Store();
        store.setId(UUID.fromString("00000000-0000-4000-8000-000000000010"));
        store.setRegionId(REGION);
        store.setChainId(CHAIN);
        store.setTeamStatus(teamStatus);
        return store;
    }

    private static StorePermissionService service(Row row, UUID userId) {
        return service(row, userId, mock(RegionPermissionResolver.class));
    }

    private static StorePermissionService service(Row row, UUID userId, RegionPermissionResolver resolver) {
        StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
        StoreChainKamRepository kams = mock(StoreChainKamRepository.class);
        RegionService regions = mock(RegionService.class);
        RegionReadService regionReads = mock(RegionReadService.class);
        UserService users = mock(UserService.class);
        PermissionService permissions = mock(PermissionService.class);

        Optional<StoreMembership> membership = Optional.empty();
        if (row.status() != null) {
            StoreMembership m = new StoreMembership();
            m.setUserId(userId);
            m.setStatus(row.status());
            m.setManager(row.manager());
            membership = Optional.of(m);
        }
        when(memberships.findByStoreIdAndUserId(any(), eq(userId))).thenReturn(membership);
        when(kams.existsByChainIdAndUserId(CHAIN, userId)).thenReturn(row.kam());
        when(permissions.isOrga(any())).thenAnswer(i -> {
            SessionPrincipal p = i.getArgument(0);
            return p != null && p.role() == Role.ORGA;
        });
        User user = new User();
        user.setId(userId);
        user.setRole(row.role());
        user.setVerified(row.verified());
        when(users.findActive(userId)).thenReturn(Optional.of(user));

        when(regions.ancestors(REGION)).thenReturn(List.of(region(PARENT), region(REGION)));
        if (row.coordinationGroupExists()) {
            when(regionReads.functionGroups(REGION)).thenReturn(List.of(new RegionReadService.FunctionGroup(
                    WorkingGroupFunction.STORES_COORDINATION, region(COORDINATION))));
        } else {
            when(regionReads.functionGroups(REGION)).thenReturn(List.of());
        }
        if (row.ambassador() == Scope.IN_DISTRICT) {
            when(resolver.isAdmin(userId, PARENT)).thenReturn(true); // ancestor ambassadors count (A10)
        }
        if (row.coordinationAdmin() == Scope.IN_DISTRICT) {
            when(resolver.isAdmin(userId, COORDINATION)).thenReturn(true);
        } else if (row.coordinationAdmin() == Scope.OUT_OF_DISTRICT) {
            when(resolver.isAdmin(userId, OTHER_COORDINATION)).thenReturn(true);
        }
        return new StorePermissionService(memberships, kams, regions, regionReads, resolver, permissions, users);
    }

    private static Region region(UUID id) {
        Region region = new Region();
        region.setId(id);
        return region;
    }
}
