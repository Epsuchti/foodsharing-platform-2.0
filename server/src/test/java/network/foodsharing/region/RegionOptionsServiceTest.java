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
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionOptionsService.OptionsChange;
import network.foodsharing.region.service.RegionOptionsService.PickupRule;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.SharedJpaSliceConfig;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** Option × actor matrix (R5), pin (R6), settings (EA22 / onboarding steps). */
@JpaSliceTest
@Import(SharedJpaSliceConfig.class)
class RegionOptionsServiceTest {

    @Autowired
    private RegionOptionsService options;

    @Autowired
    private RegionSettingsService settings;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HistoryEntryRepository history;

    private Region city;
    private User ambassador;
    private User coordAdmin;
    private User member;

    @BeforeEach
    void setUp() {
        RegionFixtures f = RegionSliceConfig.fixtures(context);
        city = f.region("zuerich", UnitType.CITY, f.region("zh", UnitType.FEDERAL_STATE, null));
        ambassador = f.user("amb", Role.AMBASSADOR);
        coordAdmin = f.user("coord");
        member = f.user("member");
        f.member(ambassador, city);
        f.admin(ambassador, city);
        f.member(member, city);
        Region coord = f.group("coord", city, WorkingGroupFunction.STORES_COORDINATION);
        f.member(coordAdmin, coord);
        f.admin(coordAdmin, coord);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static OptionsChange change(Boolean report, PickupRule rule, Boolean hiding) {
        return new OptionsChange(report, null, rule, hiding, null, null, null, "test");
    }

    @Test
    void eachOptionHasItsOwnPermission() {
        PickupRule rule = new PickupRule(true, 7, 2, 1, 24);
        TestAuth.login(TestUsers.principal(ambassador));
        // no REPORT group: ambassadors decide; STORES_COORDINATION group exists: it supersedes the ambassadors
        options.update(city.getId(), change(true, null, true));
        assertThatThrownBy(() -> options.update(city.getId(), change(null, rule, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ApiErrorCode.OPTION_NOT_EDITABLE);
                    assertThat(e.getArgs()).containsEntry("option", "pickupRule");
                });
        var permissions = options.optionPermissions(city.getId());
        assertThat(permissions.mayEditReportButtons()).isTrue();
        assertThat(permissions.mayEditPickupRule()).isFalse();
        assertThat(permissions.mayEditUserRelated()).isTrue();
        assertThat(permissions.mayEditPin()).isTrue();

        TestAuth.login(TestUsers.principal(coordAdmin));
        options.update(city.getId(), change(null, rule, null));
        assertThatThrownBy(() -> options.update(city.getId(), change(null, null, false)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getArgs()).containsEntry("option", "allowHidingInForum"));

        TestAuth.login(TestUsers.principal(member));
        RegionOptionsService.Options read = options.options(city.getId());
        assertThat(read.enableReportButton()).isTrue();
        assertThat(read.allowHidingInForum()).isTrue();
        assertThat(read.pickupRule()).isEqualTo(rule);
        assertThatThrownBy(() -> options.update(city.getId(), change(false, null, null)))
                .isInstanceOf(ApiException.class);

        List<HistoryEntry> changes = history.findAll().stream()
                .filter(entry -> entry.getAction().equals("OPTION_CHANGED")).toList();
        assertThat(changes).extracting(HistoryEntry::getField).contains("ENABLE_REPORT_BUTTON",
                "ALLOW_HIDING_IN_FORUM", "REGION_PICKUP_RULE_ACTIVE", "REGION_PICKUP_RULE_TIMESPAN_DAYS");
        assertThat(changes).filteredOn(entry -> "REGION_PICKUP_RULE_TIMESPAN_DAYS".equals(entry.getField()))
                .singleElement().satisfies(entry -> {
                    assertThat(entry.getOldValue()).isEqualTo("0");
                    assertThat(entry.getNewValue()).isEqualTo("7");
                });
    }

    @Test
    void nonMembersCannotReadOptionsAndThePinNeedsThePrFunction() {
        User outsider = RegionSliceConfig.fixtures(context).user("outsider");
        TestAuth.login(TestUsers.principal(outsider));
        assertThatThrownBy(() -> options.options(city.getId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> options.pin(city.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> options.updatePin(city.getId(), new RegionOptionsService.Pin(47.3, 8.5, "x", true)))
                .isInstanceOf(ApiException.class);

        TestAuth.login(TestUsers.principal(ambassador));
        options.updatePin(city.getId(), new RegionOptionsService.Pin(47.3, 8.5, "Treffpunkt", true));
        assertThat(options.pin(city.getId()).description()).isEqualTo("Treffpunkt");
        assertThat(options.activePin(city.getId())).isPresent();
        options.updatePin(city.getId(), new RegionOptionsService.Pin(47.3, 8.5, "Treffpunkt", false));
        assertThat(options.activePin(city.getId())).isEmpty();
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("PIN_CHANGED")).hasSize(2);
    }

    @Test
    void settingsAreReadAndChangedByAdministrators() {
        TestAuth.login(TestUsers.principal(member));
        assertThatThrownBy(() -> settings.settings(city.getId())).isInstanceOf(ApiException.class);

        TestAuth.login(TestUsers.principal(ambassador));
        var step = new RegionSettingsService.Step(null, "newcomer-meeting",
                java.util.Map.of("de", "Neuen-Treffen", "en", "Newcomer meeting"), java.util.Map.of("de", "Montag"), 1);
        var updated = settings.update(city.getId(), new RegionSettingsService.SettingsChange(
                new RegionSettingsService.IntroPickupSettings(2, false, true, true, java.util.Set.of("ONLINE"), null),
                new RegionSettingsService.OnboardingSettings(false, List.of(step)), RegionJoinMode.APPLICATION,
                "Europe/Berlin", "https://meet.example/zh", "new rules"));

        assertThat(updated.introPickups().maxActiveBookingsPerUser()).isEqualTo(2);
        assertThat(updated.introPickups().ignoredCategories()).containsExactly("ONLINE");
        assertThat(updated.onboarding().extraSteps()).singleElement().satisfies(s -> {
            assertThat(s.titles()).containsEntry("en", "Newcomer meeting");
            assertThat(s.descriptions()).containsEntry("de", "Montag");
        });
        assertThat(updated.joinMode()).isEqualTo(RegionJoinMode.APPLICATION);
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("SETTINGS_CHANGED"))
                .extracting(HistoryEntry::getField)
                .contains("maxActiveBookingsPerUser", "timeZone", "joinMode", "onboardingSteps");

        var again = settings.update(city.getId(), new RegionSettingsService.SettingsChange(null,
                new RegionSettingsService.OnboardingSettings(false, List.of()), null, null, null, null));
        assertThat(again.onboarding().extraSteps()).isEmpty();

        assertThatThrownBy(() -> settings.update(city.getId(), new RegionSettingsService.SettingsChange(null, null,
                null, "Mars/Olympus", null, null))).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.INVALID_TIME_ZONE));
    }
}
