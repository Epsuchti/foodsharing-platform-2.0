package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionStatisticsService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.Gender;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Member management (R8), permission grants (A10), region detail and statistics (R3, R13). */
@JpaSliceTest
@RecordApplicationEvents
@Import(RegionSliceConfig.class)
class RegionMembersServiceTest {

    @Autowired
    private RegionMembersService members;

    @Autowired
    private RegionReadService reads;

    @Autowired
    private RegionStatisticsService statistics;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RegionMembershipRepository memberships;

    @Autowired
    private UserRepository users;

    @Autowired
    private ApplicationEvents events;

    private RegionFixtures f;
    private Region city;
    private User ambassador;
    private User member;
    private User manager;
    private User orga;

    @BeforeEach
    void setUp() {
        f = RegionSliceConfig.fixtures(context);
        city = f.region("zuerich", UnitType.CITY, f.region("zh", UnitType.FEDERAL_STATE, null));
        ambassador = f.user("amb", Role.AMBASSADOR);
        member = f.user("member", Role.FOODSHARER);
        manager = f.user("manager");
        orga = f.user("orga", Role.ORGA);
        f.member(ambassador, city);
        f.admin(ambassador, city);
        f.member(member, city);
        f.member(manager, city);
        Region fsm = f.group("fsm", city, WorkingGroupFunction.FSMANAGEMENT);
        f.member(manager, fsm);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void membersAreListedWithFiltersForMembersOnly() {
        TestAuth.login(TestUsers.principal(member));
        var page = members.members(city.getId(), null, null, null, null, 0, 20);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(members.members(city.getId(), "amb", null, null, null, 0, 20).getContent())
                .singleElement().satisfies(view -> assertThat(view.admin()).isTrue());
        assertThat(members.members(city.getId(), null, null, Role.FOODSHARER, null, 0, 20).getContent())
                .extracting(view -> view.user().getId()).containsExactly(member.getId());
        assertThat(members.members(city.getId(), null, null, null, false, 0, 20).getTotalElements()).isEqualTo(2);
        assertThat(members.members(city.getId(), null, null, null, null, 0, 20).getContent())
                .filteredOn(view -> view.user().getId().equals(manager.getId())).singleElement()
                .satisfies(view -> assertThat(view.permissions()).containsExactly(Permission.CAN_MANAGE_USERS));

        TestAuth.login(TestUsers.principal(f.user("outsider")));
        assertThatThrownBy(() -> members.members(city.getId(), null, null, null, null, 0, 20))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void ambassadorsRemoveMembersButNotOtherAmbassadors() {
        TestAuth.login(TestUsers.principal(member));
        assertThatThrownBy(() -> members.removeMember(city.getId(), manager.getId(), "x"))
                .isInstanceOf(ApiException.class);
        TestAuth.login(TestUsers.principal(ambassador));
        assertThatThrownBy(() -> members.removeMember(city.getId(), ambassador.getId(), "x"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.CANNOT_REMOVE_AMBASSADOR));
        member.setHomeRegionId(city.getId());
        users.saveAndFlush(member);
        members.removeMember(city.getId(), member.getId(), "never showed up");
        assertThat(memberships.existsByUserIdAndRegionIdAndActiveTrue(member.getId(), city.getId())).isFalse();
        assertThat(users.findById(member.getId()).orElseThrow().getHomeRegionId()).isNull();
        assertThat(events.stream(RegionMembersService.MemberRemoved.class)).singleElement()
                .satisfies(event -> assertThat(event.reason()).isEqualTo("never showed up"));
        assertThatThrownBy(() -> members.removeMember(city.getId(), member.getId(), "x"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));
    }

    @Test
    void onlyOrgaSetsAmbassadors() {
        TestAuth.login(TestUsers.principal(ambassador));
        assertThatThrownBy(() -> members.setAdmin(city.getId(), member.getId())).isInstanceOf(ApiException.class);
        TestAuth.login(TestUsers.principal(orga));
        assertThat(members.setAdmin(city.getId(), member.getId()).admin()).isTrue();
        assertThatThrownBy(() -> members.setAdmin(city.getId(), orga.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));
        members.removeAdmin(city.getId(), member.getId());
    }

    @Test
    void grantRulesAndTheOverviewIncludeFunctionGrants() {
        TestAuth.login(TestUsers.principal(manager));
        // CAN_MANAGE_USERS holders may only grant CAN_GIVE_INTRO_PICKUPS
        members.grant(city.getId(), member.getId(), Permission.CAN_GIVE_INTRO_PICKUPS);
        assertThatThrownBy(() -> members.grant(city.getId(), member.getId(), Permission.CAN_MANAGE_SURVEYS))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        TestAuth.login(TestUsers.principal(member));
        assertThatThrownBy(() -> members.grant(city.getId(), member.getId(), Permission.CAN_GIVE_INTRO_PICKUPS))
                .isInstanceOf(ApiException.class);

        TestAuth.login(TestUsers.principal(ambassador));
        members.grant(city.getId(), member.getId(), Permission.CAN_MANAGE_SURVEYS);
        assertThatThrownBy(() -> members.grant(city.getId(), orga.getId(), Permission.CAN_MANAGE_SURVEYS))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));
        var grants = members.grants(city.getId());
        assertThat(grants).extracting(g -> g.permission() + "/" + g.source()).containsExactlyInAnyOrder(
                "CAN_GIVE_INTRO_PICKUPS/DIRECT", "CAN_MANAGE_SURVEYS/DIRECT", "CAN_MANAGE_USERS/FUNCTION:FSMANAGEMENT");
        assertThat(grants).filteredOn(g -> g.permission() == Permission.CAN_GIVE_INTRO_PICKUPS).singleElement()
                .satisfies(g -> assertThat(g.grantedBy().getId()).isEqualTo(manager.getId()));
        members.revoke(city.getId(), member.getId(), Permission.CAN_MANAGE_SURVEYS);
        assertThat(members.grants(city.getId())).hasSize(2);
    }

    @Test
    void detailAndStatistics() {
        member.setGender(Gender.FEMALE);
        member.setDateOfBirth(LocalDate.of(2000, 1, 1));
        ambassador.setDateOfBirth(LocalDate.of(1960, 1, 1));
        ambassador.setVerified(true);
        ambassador.setHomeRegionId(city.getId());
        ambassador.setLastLoginAt(RegionFixtures.now().minusSeconds(3600));
        users.saveAndFlush(member);
        users.saveAndFlush(ambassador);
        TestAuth.login(TestUsers.principal(member));

        var detail = reads.detail(city.getId());
        assertThat(detail.membership().member()).isTrue();
        assertThat(detail.ambassadors()).extracting(User::getId).containsExactly(ambassador.getId());
        assertThat(detail.memberCount()).isEqualTo(3);
        assertThat(detail.conferenceUrl()).isEqualTo("https://meet.jit.si/foodsharing-zuerich");
        assertThat(detail.statistics().genderCounts()).containsEntry("FEMALE", 1).containsEntry("NOT_SELECTED", 2);
        assertThat(detail.statistics().ageBands()).contains(new RegionStatisticsService.AgeBand("26-35", 1),
                new RegionStatisticsService.AgeBand("66+", 1), new RegionStatisticsService.AgeBand("unknown", 1));

        var refreshed = statistics.refresh(city.getId());
        assertThat(refreshed.getMemberCount()).isEqualTo(3);
        assertThat(refreshed.getActiveHomeRegionFoodsavers()).isEqualTo(1);
        assertThat(statistics.view(city.getId()).cached().getLastModified()).isNotNull();

        TestAuth.login(TestUsers.principal(f.user("outsider")));
        assertThat(reads.detail(city.getId()).statistics()).isNull();
        assertThatThrownBy(() -> statistics.view(city.getId())).isInstanceOf(ApiException.class);
    }
}
