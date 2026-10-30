package network.foodsharing.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.UUID;
import network.foodsharing.achievement.domain.Achievement;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.domain.DuplicateMode;
import network.foodsharing.achievement.mapper.AchievementMapper;
import network.foodsharing.achievement.service.AchievementService;
import network.foodsharing.api.model.AchievementUpdate;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;

/** AC1 contract: German base text for administrators, explicit validity clear. */
class AchievementMapperTest {

    AchievementMapper mapper = new AchievementMapper(mock(UserService.class), mock(UserMapper.class));

    private static Achievement achievement() {
        Achievement a = new Achievement();
        a.setId(UUID.randomUUID());
        a.setName("Putzheld");
        a.setDescription("Hat die Küche geschrubbt.");
        a.setIcon("sparkles");
        a.setVisibility(AchievementVisibility.SCOPE);
        a.setDuplicateMode(DuplicateMode.MULTIPLE);
        return a;
    }

    @Test
    void administratorsGetTheGermanBaseTextNextToTheLocalizedOne() {
        Achievement a = achievement();
        var fr = new AchievementService.Text("Héros du ménage", "A récuré la cuisine.");
        var admin = mapper.toAchievement(new AchievementService.AchievementView(a, fr.name(), fr.description(),
                Map.of("fr", fr), 2, true));
        assertThat(admin.getName()).isEqualTo("Héros du ménage");
        assertThat(admin.getBaseName()).isEqualTo("Putzheld");
        assertThat(admin.getBaseDescription()).isEqualTo("Hat die Küche geschrubbt.");
        assertThat(admin.getTranslations()).containsOnlyKeys("fr");

        var member = mapper.toAchievement(new AchievementService.AchievementView(a, fr.name(), fr.description(),
                Map.of("fr", fr), 2, false));
        assertThat(member.getBaseName()).isNull();
        assertThat(member.getBaseDescription()).isNull();
    }

    @Test
    void clearValidityDaysTravelsToTheService() {
        assertThat(mapper.toInput(new AchievementUpdate().clearValidityDays(true)).clearValidityDays()).isTrue();
        assertThat(mapper.toInput(new AchievementUpdate().validityDays(30)).clearValidityDays()).isFalse();
        assertThat(mapper.toInput(new AchievementUpdate()).validityDays()).isNull();
    }
}
