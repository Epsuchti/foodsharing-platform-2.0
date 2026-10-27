package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionJoinMode;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionMaintenanceJob;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Join / leave / home region rules (docs/conventions.md A10), welcome-bell recipients and the nightly maintenance. */
@JpaSliceTest
@RecordApplicationEvents
@Import({MembershipService.class, UserService.class, RegionClosureRepository.class, RegionMaintenanceJob.class})
class MembershipServiceTest {

    @Autowired
    private MembershipService service;

    @Autowired
    private RegionMaintenanceJob maintenanceJob;

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
    private RegionPermissionRepository permissions;

    @Autowired
    private UserRepository users;

    @Autowired
    private HistoryEntryRepository history;

    @Autowired
    private ApplicationEvents events;

    private RegionFixtures f;
    private Region country;
    private Region canton;
    private Region city;
    private Region district;
    private Region bigCity;
    private User newbie;

    @BeforeEach
    void setUp() {
        f = new RegionFixtures(regions, closure, memberships, admins, functions, permissions, users);
        country = f.region("ch", UnitType.COUNTRY, null);
        canton = f.region("zh", UnitType.FEDERAL_STATE, country);
        city = f.region("zuerich", UnitType.CITY, canton);
        district = f.region("limmattal", UnitType.REGION, canton);
        bigCity = f.region("berlin", UnitType.BIG_CITY, country);
        newbie = f.user("newbie", Role.FOODSHARER);
    }

    @Test
    void anySignedInUserJoinsAnAccessibleRegionAndGetsItAsHomeRegion() {
        MembershipService.Unit unit = service.join(newbie.getId(), city.getId());

        assertThat(unit.member()).isTrue();
        assertThat(unit.homeRegion()).isTrue();
        assertThat(memberships.existsByUserIdAndRegionIdAndActiveTrue(newbie.getId(), city.getId())).isTrue();
        assertThat(users.findById(newbie.getId()).orElseThrow().getHomeRegionId()).isEqualTo(city.getId());
        assertThat(events.stream(MembershipService.RegionMemberJoined.class))
                .containsExactly(new MembershipService.RegionMemberJoined(city.getId(), newbie.getId()));
        assertThat(actions()).contains("MEMBER_JOINED", "HOME_REGION_CHANGED");

        // a second region does not change the home region; joining again is idempotent
        service.join(newbie.getId(), district.getId());
        service.join(newbie.getId(), district.getId());
        assertThat(users.findById(newbie.getId()).orElseThrow().getHomeRegionId()).isEqualTo(city.getId());
        assertThat(actions().stream().filter("MEMBER_JOINED"::equals)).hasSize(2);
    }

    @Test
    void onlyAccessibleOpenRegionsAreJoinable() {
        for (Region region : List.of(country, canton, bigCity, f.group("ag", city, null))) {
            assertThatThrownBy(() -> service.join(newbie.getId(), region.getId()))
                    .as(region.getSlug())
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_JOINABLE));
        }
        for (UnitType type : List.of(UnitType.PART_OF_TOWN, UnitType.DISTRICT)) {
            Region accessible = f.region("x-" + type.name().toLowerCase(), type, city);
            service.join(newbie.getId(), accessible.getId());
        }
        Region applicationOnly = f.region("closed", UnitType.CITY, canton);
        applicationOnly.setJoinMode(RegionJoinMode.APPLICATION);
        regions.flush();
        assertThatThrownBy(() -> service.join(newbie.getId(), applicationOnly.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_REQUIRES_APPLICATION));
        assertThatThrownBy(() -> service.join(newbie.getId(), RegionFixtures.unknownId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_FOUND));
    }

    @Test
    void theHomeRegionCanOnlyBeLeftWhenAnotherAccessibleMembershipExists() {
        service.join(newbie.getId(), city.getId());
        assertThatThrownBy(() -> service.leave(newbie.getId(), city.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.CANNOT_LEAVE_HOME_REGION));

        service.join(newbie.getId(), district.getId());
        service.leave(newbie.getId(), city.getId());

        assertThat(memberships.existsByUserIdAndRegionIdAndActiveTrue(newbie.getId(), city.getId())).isFalse();
        assertThat(users.findById(newbie.getId()).orElseThrow().getHomeRegionId()).isEqualTo(district.getId());
        assertThat(actions()).contains("MEMBER_LEFT");
        assertThatThrownBy(() -> service.leave(newbie.getId(), city.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));
    }

    @Test
    void leavingAsAmbassadorAlsoEndsTheAdminRole() {
        User ambassador = f.user("amb", Role.AMBASSADOR);
        f.member(ambassador, district);
        f.admin(ambassador, district);
        service.leave(ambassador.getId(), district.getId());
        assertThat(admins.existsByUserIdAndRegionId(ambassador.getId(), district.getId())).isFalse();
    }

    @Test
    void homeRegionMustBeAnAccessibleMembership() {
        assertThatThrownBy(() -> service.setHomeRegion(newbie.getId(), city.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.NOT_A_MEMBER));
        f.member(newbie, canton);
        assertThatThrownBy(() -> service.setHomeRegion(newbie.getId(), canton.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_JOINABLE));
        f.member(newbie, district);
        MembershipService.Unit unit = service.setHomeRegion(newbie.getId(), district.getId());
        assertThat(unit.homeRegion()).isTrue();
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("HOME_REGION_CHANGED"))
                .singleElement().extracting(HistoryEntry::getNewValue).isEqualTo(district.getId().toString());
    }

    @Test
    void welcomeBellGoesToTheWelcomeGroupAdminsElseToTheAmbassadors() {
        User ambassador = f.user("amb", Role.AMBASSADOR);
        f.admin(ambassador, city);
        assertThat(service.welcomeRecipients(city.getId(), newbie.getId())).containsExactly(ambassador.getId());

        Region welcome = f.group("welcome", city, WorkingGroupFunction.WELCOME);
        User welcomeAdmin = f.user("welcomeadmin");
        // a WELCOME group without admins still falls back to the ambassadors
        assertThat(service.welcomeRecipients(city.getId(), newbie.getId())).containsExactly(ambassador.getId());
        f.admin(welcomeAdmin, welcome);
        assertThat(service.welcomeRecipients(city.getId(), newbie.getId())).containsExactly(welcomeAdmin.getId());
        // the joining user never notifies themself
        assertThat(service.welcomeRecipients(city.getId(), welcomeAdmin.getId())).isEmpty();
        assertThat(service.welcomeRecipients(district.getId(), newbie.getId())).isEmpty();
    }

    @Test
    void unitsListMembershipsAndAdminRoles() {
        service.join(newbie.getId(), city.getId());
        Region group = f.group("kochen", city, WorkingGroupFunction.PR);
        f.member(newbie, group);
        f.admin(newbie, group);

        List<MembershipService.Unit> units = service.units(newbie.getId());

        assertThat(units).extracting(unit -> unit.region().getSlug()).containsExactly("zuerich", "kochen");
        assertThat(units.get(0).homeRegion()).isTrue();
        assertThat(units.get(1).admin()).isTrue();
        assertThat(units.get(1).function()).isEqualTo(WorkingGroupFunction.PR);
    }

    @Test
    void maintenanceAddsAncestorMembershipsOnceAndSkipsGroups() {
        service.join(newbie.getId(), city.getId());
        Region group = f.group("kochen", city, null);
        User cook = f.user("cook");
        f.member(cook, group);

        maintenanceJob.runNow();

        assertThat(memberships.findActiveRegionIds(newbie.getId()))
                .containsExactlyInAnyOrder(city.getId(), canton.getId(), country.getId());
        assertThat(memberships.findActiveRegionIds(cook.getId())).containsExactly(group.getId());
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("MEMBER_ADDED_BY_MAINTENANCE"))
                .hasSize(2).allSatisfy(entry -> assertThat(entry.getSecondarySubjectId()).isEqualTo(newbie.getId()));
        assertThat(service.addMissingAncestorMemberships()).isZero();
        assertThat(maintenanceJob.jobName()).isEqualTo("region-maintenance");
    }

    private List<String> actions() {
        return history.findAll().stream().map(HistoryEntry::getAction).toList();
    }
}
