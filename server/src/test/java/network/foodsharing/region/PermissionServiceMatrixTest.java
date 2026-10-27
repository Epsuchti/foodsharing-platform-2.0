package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * The permission matrix of plan §9 / docs/conventions.md A10 on real tables: every actor kind × every
 * {@link PermissionService} method, incl. the working-group function → permission grants and "a function group
 * supersedes the ambassadors".
 *
 * <pre>
 * Schweiz (COUNTRY) ─ Kanton (FEDERAL_STATE) ─┬─ Stadt (CITY) ─┬─ WELCOME group     (welcomeAdmin admin, welcomeMember member)
 *                                             │                ├─ FSMANAGEMENT grp  (fsmAdmin admin + member)
 *                                             │                ├─ STORES_COORD. grp (coordAdmin admin + member)
 *                                             │                └─ plain group       (groupAdmin admin)
 *                                             └─ Other (CITY)
 * cantonAmbassador: admin of Kanton; ambassador: admin + member of Stadt; member: member of Stadt;
 * outsider: member of Other; orga: ORGA role, no scoped role. REPORT has no group in Stadt (ambassadors decide).
 * </pre>
 */
@JpaSliceTest
@Import({RegionPermissionResolver.class, RegionClosureRepository.class})
class PermissionServiceMatrixTest {

    @Autowired
    private PermissionService permissions;

    @Autowired
    private RegionRepository regions;

    @Autowired
    private RegionClosureRepository closure;

    @Autowired
    private RegionMembershipRepository memberships;

    @Autowired
    private RegionAdminRepository admins;

    @Autowired
    private RegionFunctionRepository functions;

    @Autowired
    private RegionPermissionRepository regionPermissions;

    @Autowired
    private UserRepository users;

    private Region stadt;
    private Region kanton;
    private Region welcomeGroup;
    private final Map<String, User> actors = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        RegionFixtures f = new RegionFixtures(regions, closure, memberships, admins, functions, regionPermissions, users);
        Region schweiz = f.region("schweiz", UnitType.COUNTRY, null);
        kanton = f.region("kanton", UnitType.FEDERAL_STATE, schweiz);
        stadt = f.region("stadt", UnitType.CITY, kanton);
        Region other = f.region("other", UnitType.CITY, kanton);
        welcomeGroup = f.group("welcome", stadt, WorkingGroupFunction.WELCOME);
        Region fsmGroup = f.group("fsm", stadt, WorkingGroupFunction.FSMANAGEMENT);
        Region coordGroup = f.group("coord", stadt, WorkingGroupFunction.STORES_COORDINATION);
        Region plainGroup = f.group("plain", stadt, null);

        actors.put("orga", f.user("orga", Role.ORGA));
        actors.put("cantonAmbassador", f.user("cantonamb", Role.AMBASSADOR));
        actors.put("ambassador", f.user("amb", Role.AMBASSADOR));
        actors.put("welcomeAdmin", f.user("welcomeadmin"));
        actors.put("welcomeMember", f.user("welcomemember"));
        actors.put("fsmAdmin", f.user("fsm"));
        actors.put("coordAdmin", f.user("coord"));
        actors.put("groupAdmin", f.user("groupadmin"));
        actors.put("member", f.user("member", Role.FOODSHARER));
        actors.put("outsider", f.user("outsider"));

        f.admin(actors.get("cantonAmbassador"), kanton);
        f.admin(actors.get("ambassador"), stadt);
        f.member(actors.get("ambassador"), stadt);
        f.admin(actors.get("welcomeAdmin"), welcomeGroup);
        f.member(actors.get("welcomeAdmin"), welcomeGroup);
        f.member(actors.get("welcomeMember"), welcomeGroup);
        f.admin(actors.get("fsmAdmin"), fsmGroup);
        f.member(actors.get("fsmAdmin"), fsmGroup);
        f.admin(actors.get("coordAdmin"), coordGroup);
        f.member(actors.get("coordAdmin"), coordGroup);
        f.admin(actors.get("groupAdmin"), plainGroup);
        f.member(actors.get("groupAdmin"), plainGroup);
        f.member(actors.get("member"), stadt);
        f.member(actors.get("outsider"), other);
    }

    /** Row = check, columns in the order of {@link #actors}. */
    private void row(String name, BiPredicate<SessionPrincipal, User> check, boolean... expected) {
        List<String> keys = List.copyOf(actors.keySet());
        assertThat(expected).as(name + " has a value per actor").hasSize(keys.size());
        User member = actors.get("member");
        for (int i = 0; i < keys.size(); i++) {
            SessionPrincipal principal = TestUsers.principal(actors.get(keys.get(i)));
            assertThat(check.test(principal, member)).as(name + " for " + keys.get(i)).isEqualTo(expected[i]);
        }
    }

    @Test
    void scopedRoleMatrix() {
        //                                                                   orga   cAmb   amb    wAdm   wMem   fsm    coord  grpAd  member outsd
        row("isMemberOf(stadt)", (p, u) -> permissions.isMemberOf(p, stadt.getId()),
                false, false, true, false, false, false, false, false, true, false);
        row("isAmbassador(stadt)", (p, u) -> permissions.isAmbassador(p, stadt.getId()),
                false, false, true, false, false, false, false, false, false, false);
        row("isAmbassadorOrOrga(stadt)", (p, u) -> permissions.isAmbassadorOrOrga(p, stadt.getId()),
                true, false, true, false, false, false, false, false, false, false);
        row("isAmbassadorOfAny", (p, u) -> permissions.isAmbassadorOfAny(p),
                false, true, true, true, false, true, true, true, false, false);
        row("hasFunction(WELCOME, stadt): group exists -> its admins", (p, u) ->
                        permissions.hasFunction(p, WorkingGroupFunction.WELCOME, stadt.getId()),
                true, false, false, true, false, false, false, false, false, false);
        row("hasFunction(STORES_COORDINATION, stadt): group supersedes ambassadors", (p, u) ->
                        permissions.hasFunction(p, WorkingGroupFunction.STORES_COORDINATION, stadt.getId()),
                true, false, false, false, false, false, true, false, false, false);
        row("hasFunction(REPORT, stadt): no group -> ambassadors", (p, u) ->
                        permissions.hasFunction(p, WorkingGroupFunction.REPORT, stadt.getId()),
                true, false, true, false, false, false, false, false, false, false);
        row("mayAdministrateRegion(stadt): ambassadors of stadt or above, FSMANAGEMENT admins", (p, u) ->
                        permissions.mayAdministrateRegion(p, stadt.getId()),
                true, true, true, false, false, true, false, false, false, false);
        row("mayAdministrateRegion(kanton)", (p, u) -> permissions.mayAdministrateRegion(p, kanton.getId()),
                true, true, false, false, false, false, false, false, false, false);
        row("mayAdministrateUser(member of stadt)", (p, u) -> permissions.mayAdministrateUser(p, u.getId()),
                true, true, true, false, false, true, false, false, false, false);
        row("mayAdministrateUser(outsider of other)", (p, u) ->
                        permissions.mayAdministrateUser(p, actors.get("outsider").getId()),
                true, true, false, false, false, false, false, false, false, false);
    }

    @Test
    void regionPermissionMatrixIncludesFunctionGrants() {
        //                                                                   orga   cAmb   amb    wAdm   wMem   fsm    coord  grpAd  member outsd
        row("has(CAN_GIVE_INTRO_PICKUPS, stadt): WELCOME members", (p, u) ->
                        permissions.has(p, Permission.CAN_GIVE_INTRO_PICKUPS, stadt.getId()),
                true, false, false, true, true, false, false, false, false, false);
        row("has(CAN_MANAGE_USERS, stadt): FSMANAGEMENT members", (p, u) ->
                        permissions.has(p, Permission.CAN_MANAGE_USERS, stadt.getId()),
                true, false, false, false, false, true, false, false, false, false);
        row("has(CAN_USE_AUTOMATIONS, stadt): STORES_COORDINATION members", (p, u) ->
                        permissions.has(p, Permission.CAN_USE_AUTOMATIONS, stadt.getId()),
                true, false, false, false, false, false, true, false, false, false);
        row("has(CAN_MANAGE_SURVEYS, stadt): no VOTING group", (p, u) ->
                        permissions.has(p, Permission.CAN_MANAGE_SURVEYS, stadt.getId()),
                true, false, false, false, false, false, false, false, false, false);
        row("has(CAN_GIVE_INTRO_PICKUPS, kanton): grants are per region", (p, u) ->
                        permissions.has(p, Permission.CAN_GIVE_INTRO_PICKUPS, kanton.getId()),
                true, false, false, false, false, false, false, false, false, false);
        row("hasAnywhere(CAN_GIVE_INTRO_PICKUPS)", (p, u) ->
                        permissions.hasAnywhere(p, Permission.CAN_GIVE_INTRO_PICKUPS),
                true, false, false, true, true, false, false, false, false, false);
    }

    @Test
    void directGrantsAndEffectivePermissions() {
        RegionFixtures f = new RegionFixtures(regions, closure, memberships, admins, functions, regionPermissions, users);
        User member = actors.get("member");
        f.grant(member, stadt, Permission.CAN_GIVE_INTRO_PICKUPS);
        SessionPrincipal principal = TestUsers.principal(member);

        assertThat(permissions.has(principal, Permission.CAN_GIVE_INTRO_PICKUPS, stadt.getId())).isTrue();
        assertThat(permissions.effectivePermissions(principal))
                .containsExactly("CAN_GIVE_INTRO_PICKUPS@" + stadt.getId());
        assertThat(permissions.effectivePermissions(TestUsers.principal(actors.get("coordAdmin")))).containsExactly(
                "CAN_SEE_ALL_AUTOMATION_DECISIONS@" + stadt.getId(),
                "CAN_SEE_USER_PICKUP_COUNT_GROUPING@" + stadt.getId(),
                "CAN_USE_AUTOMATIONS@" + stadt.getId());
        assertThat(permissions.effectivePermissions(TestUsers.principal(actors.get("orga"))))
                .hasSize(Permission.values().length).allMatch(name -> name.endsWith("@*"));
        assertThat(permissions.effectivePermissions(TestUsers.principal(actors.get("outsider")))).isEmpty();
        assertThat(permissions.effectivePermissions(null)).isEmpty();
    }

    @Test
    void requireVariantsThrow401And403() {
        SessionPrincipal member = TestUsers.principal(actors.get("member"));
        assertThatThrownBy(() -> permissions.requireMayAdministrateRegion(null, stadt.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(401));
        assertThatThrownBy(() -> permissions.requireMayAdministrateRegion(member, stadt.getId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(403));
        assertThatThrownBy(() -> permissions.require(member, Permission.CAN_MANAGE_USERS, stadt.getId()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> permissions.requireFunction(member, WorkingGroupFunction.PR, stadt.getId()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> permissions.requireOrga(member)).isInstanceOf(ApiException.class);
        permissions.requireMember(member, stadt.getId());
        permissions.requireAmbassadorOrOrga(TestUsers.principal(actors.get("ambassador")), stadt.getId());
        permissions.requireMayAdministrateUser(TestUsers.principal(actors.get("fsmAdmin")), actors.get("member").getId());
        permissions.require(TestUsers.principal(actors.get("welcomeMember")), Permission.CAN_GIVE_INTRO_PICKUPS,
                stadt.getId());
    }

    @Test
    void groupAdminsAreAmbassadorsOfTheirGroupOnly() {
        SessionPrincipal welcomeAdmin = TestUsers.principal(actors.get("welcomeAdmin"));
        assertThat(permissions.isAmbassador(welcomeAdmin, welcomeGroup.getId())).isTrue();
        assertThat(permissions.mayAdministrateRegion(welcomeAdmin, welcomeGroup.getId())).isFalse();
        assertThat(permissions.isMemberOf(welcomeAdmin, welcomeGroup.getId())).isTrue();
    }
}
