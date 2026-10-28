package network.foodsharing.achievement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.service.AchievementRules;
import network.foodsharing.achievement.service.AchievementRules.Viewer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** AC1 visibility matrix (5 visibilities × viewers), award and validity rules. */
class AchievementVisibilityTest {

    static final Viewer ADMIN = new Viewer(true, true, true, false);
    static final Viewer RECIPIENT = new Viewer(false, true, false, true);
    static final Viewer STORE_MANAGER_MEMBER = new Viewer(false, true, true, false);
    static final Viewer MEMBER = new Viewer(false, true, false, false);
    static final Viewer STORE_MANAGER_OUTSIDER = new Viewer(false, false, true, false);
    static final Viewer OUTSIDER = new Viewer(false, false, false, false);

    @ParameterizedTest
    @CsvSource({
            // visibility, admin, recipient, store manager member, member, store manager outsider, outsider
            "HIDDEN,         true, false, false, false, false, false",
            "PRIVATE,        true, true,  false, false, false, false",
            "STORE_MANAGERS, true, true,  true,  false, false, false",
            "SCOPE,          true, true,  true,  true,  false, false",
            "GLOBAL,         true, true,  true,  true,  true,  true"})
    void awardVisibility(AchievementVisibility visibility, boolean admin, boolean recipient, boolean manager,
                         boolean member, boolean managerOutside, boolean outsider) {
        assertThat(AchievementRules.awardVisible(visibility, ADMIN)).isEqualTo(admin);
        assertThat(AchievementRules.awardVisible(visibility, RECIPIENT)).isEqualTo(recipient);
        assertThat(AchievementRules.awardVisible(visibility, STORE_MANAGER_MEMBER)).isEqualTo(manager);
        assertThat(AchievementRules.awardVisible(visibility, MEMBER)).isEqualTo(member);
        assertThat(AchievementRules.awardVisible(visibility, STORE_MANAGER_OUTSIDER)).isEqualTo(managerOutside);
        assertThat(AchievementRules.awardVisible(visibility, OUTSIDER)).isEqualTo(outsider);
    }

    @Test
    void definitionsHiddenOnlyForAdminsAndOnlyForMembers() {
        assertThat(AchievementRules.definitionVisible(AchievementVisibility.HIDDEN, ADMIN)).isTrue();
        assertThat(AchievementRules.definitionVisible(AchievementVisibility.HIDDEN, MEMBER)).isFalse();
        assertThat(AchievementRules.definitionVisible(AchievementVisibility.PRIVATE, MEMBER)).isTrue();
        assertThat(AchievementRules.definitionVisible(AchievementVisibility.GLOBAL, OUTSIDER)).isFalse();
    }

    @Test
    void nobodyAwardsThemself() {
        assertThat(AchievementRules.mayAward(true, false)).isTrue();
        assertThat(AchievementRules.mayAward(true, true)).isFalse();
        assertThat(AchievementRules.mayAward(false, false)).isFalse();
    }

    @Test
    void validityExplicitDateWinsElseDaysElseUnlimited() {
        LocalDate day = LocalDate.of(2026, 10, 28);
        assertThat(AchievementRules.validUntil(day, 365, LocalDate.of(2027, 1, 1))).isEqualTo("2027-01-01");
        assertThat(AchievementRules.validUntil(day, 365, null)).isEqualTo("2027-10-28");
        assertThat(AchievementRules.validUntil(day, null, null)).isNull();
    }

    @Test
    void overrideExtendsToTheLaterEnd() {
        LocalDate early = LocalDate.of(2026, 12, 1);
        LocalDate late = LocalDate.of(2027, 6, 1);
        assertThat(AchievementRules.extended(early, late)).isEqualTo(late);
        assertThat(AchievementRules.extended(late, early)).isEqualTo(late);
        assertThat(AchievementRules.extended(null, early)).isNull();
        assertThat(AchievementRules.extended(early, null)).isNull();
    }
}
