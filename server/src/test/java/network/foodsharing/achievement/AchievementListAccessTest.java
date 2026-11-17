package network.foodsharing.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.achievement.domain.Achievement;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.domain.DuplicateMode;
import network.foodsharing.achievement.repository.AchievementI18nRepository;
import network.foodsharing.achievement.repository.AchievementRepository;
import network.foodsharing.achievement.repository.AwardedAchievementRepository;
import network.foodsharing.achievement.service.AchievementPermissionService;
import network.foodsharing.achievement.service.AchievementRules;
import network.foodsharing.achievement.service.AchievementService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AC1: the region's achievement list and a hidden definition, per viewer (was AchievementFlowIT). */
class AchievementListAccessTest {

    static final UUID REGION = UUID.randomUUID();

    AchievementRepository achievements = mock(AchievementRepository.class);
    AwardedAchievementRepository awards = mock(AwardedAchievementRepository.class);
    AchievementPermissionService access = mock(AchievementPermissionService.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    AchievementService service = new AchievementService(achievements, mock(AchievementI18nRepository.class), awards,
            access, actors, mock(UserService.class), mock(RegionService.class),
            mock(HistoryService.class, RETURNS_DEEP_STUBS), mock(NotificationService.class),
            Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));

    SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER, "f@x");
    Achievement scope = achievement(AchievementVisibility.SCOPE);
    Achievement hidden = achievement(AchievementVisibility.HIDDEN);

    static Achievement achievement(AchievementVisibility visibility) {
        Achievement a = new Achievement();
        a.setId(UUID.randomUUID());
        a.setRegionId(REGION);
        a.setName(visibility.name());
        a.setIcon("star");
        a.setVisibility(visibility);
        a.setDuplicateMode(DuplicateMode.MULTIPLE);
        return a;
    }

    @BeforeEach
    void setUp() {
        when(actors.requirePrincipal()).thenReturn(actor);
        when(achievements.findByRegionIdAndDeletedAtIsNullOrderByNameAscIdAsc(REGION)).thenReturn(List.of(hidden, scope));
        when(achievements.findById(hidden.getId())).thenReturn(Optional.of(hidden));
        when(awards.countActive(any())).thenReturn(List.of());
    }

    private void viewer(boolean admin, boolean member) {
        when(access.viewer(actor, REGION, null)).thenReturn(new AchievementRules.Viewer(admin, member, false, false));
    }

    @Test
    void neitherMemberNorAdministratorIsRefused() {
        viewer(false, false);
        assertThatThrownBy(() -> service.list(REGION)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
    }

    @Test
    void membersDoNotSeeHiddenDefinitionsAdministratorsDo() {
        viewer(false, true);
        assertThat(service.list(REGION)).extracting(view -> view.achievement().getId()).containsExactly(scope.getId());
        assertThatThrownBy(() -> service.get(hidden.getId())).isInstanceOf(ApiException.class);
        viewer(true, true);
        assertThat(service.list(REGION)).hasSize(2).allSatisfy(view -> assertThat(view.mayAdminister()).isTrue());
    }
}
