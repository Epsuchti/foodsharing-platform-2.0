package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.region.domain.ApplyType;
import network.foodsharing.region.domain.GroupApplicationStatus;
import network.foodsharing.region.domain.GroupCategory;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.GroupService;
import network.foodsharing.region.service.GroupService.GroupInput;
import network.foodsharing.region.service.RegionNotificationListener;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
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

/** Working-group rules (plan §13.2 R9–R11): apply types × membership × requirements, admins, restricted functions. */
@JpaSliceTest
@RecordApplicationEvents
@Import(RegionSliceConfig.class)
class GroupServiceTest {

    @Autowired
    private GroupService groups;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RegionRepository regions;

    @Autowired
    private RegionMembershipRepository memberships;

    @Autowired
    private UserRepository users;

    @Autowired
    private HistoryEntryRepository history;

    @Autowired
    private ApplicationEvents events;

    private RegionFixtures f;
    private Region city;
    private Region everybody;
    private User ambassador;
    private User member;
    private User outsider;
    private User orga;

    @BeforeEach
    void setUp() {
        f = RegionSliceConfig.fixtures(context);
        Region country = f.region("ch", UnitType.COUNTRY, null);
        city = f.region("zuerich", UnitType.CITY, country);
        ambassador = f.user("amb", Role.AMBASSADOR);
        member = f.user("member");
        outsider = f.user("outsider");
        orga = f.user("orga", Role.ORGA);
        f.member(ambassador, city);
        f.admin(ambassador, city);
        f.member(member, city);
        everybody = f.group("pr", city, WorkingGroupFunction.PR);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private void as(User user) {
        TestAuth.login(TestUsers.principal(user));
    }

    private static GroupInput input(String name, ApplyType applyType, WorkingGroupFunction function) {
        return new GroupInput(name, "teaser", "desc", null, GroupCategory.PROJECT, applyType, null, null, null,
                function, null);
    }

    @Test
    void applyAcceptAndDeclineFlow() {
        as(member);
        var application = groups.apply(everybody.getId(), "I like taking photos");
        assertThat(application.application().getStatus()).isEqualTo(GroupApplicationStatus.PENDING);
        assertThat(events.stream(GroupService.ApplicationSubmitted.class)).hasSize(1);
        assertThatThrownBy(() -> groups.apply(everybody.getId(), "again")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ALREADY_APPLIED));
        assertThat(groups.detail(everybody.getId()).applied()).isTrue();
        assertThat(groups.detail(everybody.getId()).mayApply()).isFalse();

        as(member);
        assertThatThrownBy(() -> groups.applications(everybody.getId(), null)).isInstanceOf(ApiException.class);

        as(ambassador);
        // the group has no admin: ambassadors of the region are no group admins, ORGA decides
        as(orga);
        assertThat(groups.applications(everybody.getId(), null)).hasSize(1);
        groups.decide(everybody.getId(), application.application().getId(), true, "Welcome!");
        assertThat(memberships.existsByUserIdAndRegionIdAndActiveTrue(member.getId(), everybody.getId())).isTrue();
        assertThat(events.stream(GroupService.ApplicationDecided.class)).singleElement()
                .satisfies(event -> assertThat(event.accepted()).isTrue());
        assertThatThrownBy(() -> groups.decide(everybody.getId(), application.application().getId(), false, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.APPLICATION_NOT_PENDING));

        as(member);
        assertThatThrownBy(() -> groups.apply(everybody.getId(), "x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ALREADY_MEMBER));
        assertThat(history.findAll()).extracting(entry -> entry.getAction())
                .contains("APPLICATION_SUBMITTED", "APPLICATION_ACCEPTED");
    }

    @Test
    void applyingNeedsTheRegionMembershipAndAnOpenApplyType() {
        as(outsider);
        assertThat(groups.detail(everybody.getId()).mayApply()).isFalse();
        assertThatThrownBy(() -> groups.apply(everybody.getId(), "hi")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));

        Region nobody = f.group("lager", city, null);
        nobody.setApplyType(ApplyType.NOBODY);
        regions.flush();
        as(member);
        assertThatThrownBy(() -> groups.apply(nobody.getId(), "hi")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.GROUP_APPLICATIONS_CLOSED));
        assertThatThrownBy(() -> groups.joinOpen(nobody.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.GROUP_NOT_OPEN));

        Region global = f.region("global", UnitType.WORKING_GROUP, null);
        Region globalGroup = f.group("it", global, null);
        as(outsider);
        assertThat(groups.detail(globalGroup.getId()).mayApply()).isTrue();
    }

    @Test
    void openGroupsAreJoinedDirectlyAndArchivedGroupsAcceptNobody() {
        Region open = f.group("kochen", city, null);
        open.setApplyType(ApplyType.OPEN);
        regions.flush();
        as(member);
        assertThat(groups.detail(open.getId()).mayJoin()).isTrue();
        groups.joinOpen(open.getId());
        assertThatThrownBy(() -> groups.joinOpen(open.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ALREADY_MEMBER));

        as(orga);
        groups.archive(everybody.getId(), "done");
        as(member);
        assertThatThrownBy(() -> groups.apply(everybody.getId(), "x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.GROUP_APPLICATIONS_CLOSED));
        assertThat(groups.groupsIn(city.getId(), false)).extracting(view -> view.group().getSlug())
                .containsExactly("kochen");
        assertThat(groups.groupsIn(city.getId(), true)).hasSize(2);
    }

    @Test
    void requirementsAreCheckedWithReasons() {
        everybody.setMinWeeks(4);
        everybody.setMinPickups(1);
        regions.flush();
        User fresh = f.user("fresh");
        fresh.setRegisteredAt(RegionFixtures.now().minus(Duration.ofDays(10)));
        users.saveAndFlush(fresh);
        f.member(fresh, city);
        as(fresh);
        var check = groups.detail(everybody.getId()).requirementCheck();
        assertThat(check.weeksOk()).isFalse();
        assertThat(check.weeks()).isEqualTo(1);
        assertThat(check.pickupsOk()).isFalse();
        assertThat(check.bananasOk()).isTrue();
        assertThatThrownBy(() -> groups.apply(everybody.getId(), "x")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ApiErrorCode.GROUP_REQUIREMENTS_NOT_MET);
            assertThat(e.getArgs()).containsEntry("weeks", "4").containsEntry("pickups", "1");
        });
    }

    @Test
    void ambassadorsCreateGroupsButRestrictedFunctionsNeedOrga() {
        as(member);
        assertThatThrownBy(() -> groups.create(city.getId(), input("Neu", ApplyType.OPEN, null)))
                .isInstanceOf(ApiException.class);

        as(ambassador);
        var created = groups.create(city.getId(), input("Neue Gruppe", ApplyType.OPEN, WorkingGroupFunction.WELCOME));
        assertThat(created.group().getSlug()).isEqualTo("neue-gruppe");
        assertThat(created.admin()).isTrue();
        assertThat(created.function()).isEqualTo(WorkingGroupFunction.WELCOME);
        assertThatThrownBy(() -> groups.create(city.getId(), input("Report", ApplyType.NOBODY,
                WorkingGroupFunction.REPORT))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.RESTRICTED_FUNCTION));
        assertThatThrownBy(() -> groups.create(city.getId(), input("Welcome 2", ApplyType.OPEN,
                WorkingGroupFunction.WELCOME))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.DUPLICATE_FUNCTION_GROUP));

        as(orga);
        var report = groups.create(city.getId(), input("Report", ApplyType.NOBODY, WorkingGroupFunction.REPORT));
        // the group admin may not edit a restricted function group, ORGA may
        User reportAdmin = f.user("reportadmin");
        f.member(reportAdmin, report.group());
        f.admin(reportAdmin, report.group());
        as(reportAdmin);
        assertThat(groups.detail(report.group().getId()).mayEdit()).isFalse();
        assertThatThrownBy(() -> groups.update(report.group().getId(), input("X", null, null)))
                .isInstanceOf(ApiException.class);
        as(orga);
        assertThat(groups.update(report.group().getId(), input("Meldeteam", null, null)).group().getName())
                .isEqualTo("Meldeteam");
    }

    @Test
    void adminsManageMembersAndMembersMayLeave() {
        as(orga);
        groups.addMember(everybody.getId(), member.getId());
        assertThatThrownBy(() -> groups.addMember(everybody.getId(), member.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ALREADY_MEMBER));
        groups.setAdmin(everybody.getId(), member.getId());
        assertThatThrownBy(() -> groups.setAdmin(everybody.getId(), outsider.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));
        assertThat(groups.members(everybody.getId())).singleElement().satisfies(m -> assertThat(m.admin()).isTrue());

        as(member);
        groups.addMember(everybody.getId(), outsider.getId());
        as(outsider);
        assertThatThrownBy(() -> groups.removeMember(everybody.getId(), member.getId(), "x"))
                .isInstanceOf(ApiException.class);
        groups.removeMember(everybody.getId(), outsider.getId(), "leaving");
        assertThat(memberships.existsByUserIdAndRegionIdAndActiveTrue(outsider.getId(), everybody.getId())).isFalse();
    }

    @Test
    void groupMailIsForMembersAndRateLimited() {
        as(outsider);
        assertThatThrownBy(() -> groups.sendMail(everybody.getId(), "Hi", "Body")).isInstanceOf(ApiException.class);
        f.member(member, everybody);
        as(member);
        for (int i = 0; i < 5; i++) {
            assertThat(groups.sendMail(everybody.getId(), "Hi", "Body")).isEqualTo(1);
        }
        assertThatThrownBy(() -> groups.sendMail(everybody.getId(), "Hi", "Body"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.TOO_MANY_REQUESTS));
        assertThat(events.stream(GroupService.GroupMailRequested.class)).hasSize(5);
    }

    @Test
    void cancellingTheOwnApplication() {
        as(member);
        assertThatThrownBy(() -> groups.cancelMine(everybody.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.APPLICATION_NOT_FOUND));
        groups.apply(everybody.getId(), "x");
        groups.cancelMine(everybody.getId());
        assertThat(groups.detail(everybody.getId()).applied()).isFalse();
        assertThat(RegionNotificationListener.applicationIdentifier(everybody.getId(), member.getId()))
                .isEqualTo("GROUP_APPLICATION_RECEIVED-" + everybody.getId() + "-" + member.getId());
    }
}
