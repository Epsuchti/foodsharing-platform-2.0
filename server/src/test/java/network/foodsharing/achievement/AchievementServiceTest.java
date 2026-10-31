package network.foodsharing.achievement;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.achievement.domain.Achievement;
import network.foodsharing.achievement.domain.AchievementAction;
import network.foodsharing.achievement.domain.AchievementSystemKey;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.domain.AwardedAchievement;
import network.foodsharing.achievement.domain.DuplicateMode;
import network.foodsharing.achievement.repository.AchievementI18nRepository;
import network.foodsharing.achievement.repository.AchievementRepository;
import network.foodsharing.achievement.repository.AwardedAchievementRepository;
import network.foodsharing.achievement.service.AchievementPermissionService;
import network.foodsharing.achievement.service.AchievementRules;
import network.foodsharing.achievement.service.AchievementService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** AC1: permissions, duplicate modes, validity, system protection, bells and history. */
class AchievementServiceTest {

    static final UUID REGION = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    AchievementRepository achievements = mock(AchievementRepository.class);
    AchievementI18nRepository texts = mock(AchievementI18nRepository.class);
    AwardedAchievementRepository awards = mock(AwardedAchievementRepository.class);
    AchievementPermissionService access = mock(AchievementPermissionService.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    UserService users = mock(UserService.class);
    RegionService regions = mock(RegionService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    NotificationService notifications = mock(NotificationService.class);
    AchievementService service = new AchievementService(achievements, texts, awards, access, actors, users, regions,
            history, notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "a@x");
    Achievement achievement = new Achievement();
    User recipient = new User();

    @BeforeEach
    void setUp() {
        achievement.setId(UUID.randomUUID());
        achievement.setRegionId(REGION);
        achievement.setName("Putzheld");
        achievement.setIcon("sparkles");
        achievement.setVisibility(AchievementVisibility.SCOPE);
        achievement.setDuplicateMode(DuplicateMode.MULTIPLE);
        recipient.setId(UUID.randomUUID());
        when(actors.requirePrincipal()).thenReturn(actor);
        when(achievements.findById(achievement.getId())).thenReturn(Optional.of(achievement));
        when(achievements.findAllById(any())).thenReturn(List.of(achievement));
        when(users.findActive(recipient.getId())).thenReturn(Optional.of(recipient));
        when(access.mayAdminister(actor, achievement)).thenReturn(true);
        when(access.isActiveMember(recipient.getId(), REGION)).thenReturn(true);
        when(awards.save(any(AwardedAchievement.class))).thenAnswer(inv -> {
            AwardedAchievement a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
    }

    @Test
    void onlyAdministratorsAwardAndNeverToThemselves() {
        when(access.mayAdminister(actor, achievement)).thenReturn(false);
        assertThat(codeOf(() -> service.award(achievement.getId(), recipient.getId(), null, null)))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(access.mayAdminister(actor, achievement)).thenReturn(true);
        assertThat(codeOf(() -> service.award(achievement.getId(), actor.userId(), null, null)))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verify(awards, never()).save(any());
    }

    @Test
    void recipientMustBeActiveMemberOfTheRegion() {
        when(access.isActiveMember(recipient.getId(), REGION)).thenReturn(false);
        assertThat(codeOf(() -> service.award(achievement.getId(), recipient.getId(), null, null)))
                .isEqualTo(ApiErrorCode.ACHIEVEMENT_RECIPIENT_NOT_MEMBER);
        assertThat(codeOf(() -> service.award(achievement.getId(), UUID.randomUUID(), null, null)))
                .isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    @Test
    void multipleAddsASecondAwardWithValidityFromDaysAndSendsABell() {
        achievement.setValidityDays(30);
        AwardedAchievement existing = award(LocalDate.of(2026, 11, 1));
        when(awards.findByUserIdAndAchievementIdAndRevokedAtIsNullOrderByAwardedAtDescIdDesc(recipient.getId(),
                achievement.getId())).thenReturn(List.of(existing));
        var view = service.award(achievement.getId(), recipient.getId(), null, " geschrubbt ");
        assertThat(view.award()).isNotSameAs(existing);
        assertThat(view.award().getValidUntil()).isEqualTo("2026-11-27");
        assertThat(view.award().getNotice()).isEqualTo("geschrubbt");
        assertThat(view.award().getAwardedBy()).isEqualTo(actor.userId());
        verify(history).event(AchievementAction.AWARDED, achievement.getId());
        verify(notifications).notify(any());
    }

    @Test
    void overrideExtendsTheActiveAward() {
        achievement.setDuplicateMode(DuplicateMode.OVERRIDE);
        AwardedAchievement existing = award(LocalDate.of(2027, 6, 1));
        when(awards.findByUserIdAndAchievementIdAndRevokedAtIsNullOrderByAwardedAtDescIdDesc(recipient.getId(),
                achievement.getId())).thenReturn(List.of(existing));
        var view = service.award(achievement.getId(), recipient.getId(), LocalDate.of(2027, 1, 1), "neu");
        assertThat(view.award()).isSameAs(existing);
        assertThat(existing.getValidUntil()).isEqualTo("2027-06-01");
        assertThat(existing.getNotice()).isEqualTo("neu");
        verify(awards, never()).save(any());
    }

    @Test
    void systemAchievementsCannotBeDeletedOthersAreSoftDeleted() {
        when(access.mayAdminister(actor, REGION)).thenReturn(true);
        org.mockito.Mockito.doCallRealMethod().when(access).requireAdminister(actor, REGION);
        achievement.setSystemKey(AchievementSystemKey.KAM_CERTIFICATE);
        assertThat(codeOf(() -> service.delete(achievement.getId())))
                .isEqualTo(ApiErrorCode.ACHIEVEMENT_SYSTEM_PROTECTED);
        achievement.setSystemKey(null);
        service.delete(achievement.getId());
        assertThat(achievement.getDeletedAt()).isEqualTo(NOW);
        assertThat(codeOf(() -> service.get(achievement.getId()))).isEqualTo(ApiErrorCode.ACHIEVEMENT_NOT_FOUND);
    }

    @Test
    void autoAwardIsSystemActorWithValidityFromProperties() {
        achievement.setSystemKey(AchievementSystemKey.HYGIENE_CERTIFICATE);
        achievement.setDuplicateMode(DuplicateMode.OVERRIDE);
        when(achievements.findBySystemKey(AchievementSystemKey.HYGIENE_CERTIFICATE))
                .thenReturn(Optional.of(achievement));
        AwardedAchievement award = service.autoAward(AchievementSystemKey.HYGIENE_CERTIFICATE, recipient.getId(),
                365).orElseThrow();
        assertThat(award.getValidUntil()).isEqualTo("2027-10-28");
        assertThat(award.getAwardedBy()).isNull();
        verify(history).event(AchievementAction.AUTO_AWARDED, achievement.getId());
        // a renewed pass replaces the end date of the existing certificate (also an earlier one)
        when(awards.findByUserIdAndAchievementIdAndRevokedAtIsNullOrderByAwardedAtDescIdDesc(recipient.getId(),
                achievement.getId())).thenReturn(List.of(award));
        service.autoAward(AchievementSystemKey.HYGIENE_CERTIFICATE, recipient.getId(), 30);
        assertThat(award.getValidUntil()).isEqualTo("2026-11-27");
        assertThat(service.autoAward(AchievementSystemKey.KAM_CERTIFICATE, recipient.getId(), null)).isEmpty();
    }

    @Test
    void revokeIsSoftAndRecorded() {
        AwardedAchievement existing = award(null);
        when(awards.findById(existing.getId())).thenReturn(Optional.of(existing));
        service.revoke(existing.getId());
        assertThat(existing.getRevokedAt()).isEqualTo(NOW);
        assertThat(existing.getRevokedBy()).isEqualTo(actor.userId());
        verify(history).event(AchievementAction.REVOKED, achievement.getId());
        assertThat(codeOf(() -> service.revoke(existing.getId()))).isEqualTo(ApiErrorCode.AWARD_NOT_FOUND);
    }

    @Test
    void userAwardsAreFilteredByVisibility() {
        AwardedAchievement visible = award(null);
        Achievement hidden = new Achievement();
        hidden.setId(UUID.randomUUID());
        hidden.setRegionId(REGION);
        hidden.setName("Vertrauensperson");
        hidden.setIcon("handshake");
        hidden.setVisibility(AchievementVisibility.HIDDEN);
        AwardedAchievement secret = award(null);
        secret.setAchievementId(hidden.getId());
        when(awards.findByUserIdAndRevokedAtIsNullOrderByAwardedAtDescIdDesc(recipient.getId()))
                .thenReturn(List.of(visible, secret));
        when(achievements.findAllById(any())).thenReturn(List.of(achievement, hidden));
        when(access.viewer(any(), any(), any())).thenReturn(new AchievementRules.Viewer(false, true, false, false));
        var list = service.userAwards(recipient.getId());
        assertThat(list).extracting(v -> v.award().getId()).containsExactly(visible.getId());
    }

    @Test
    void createNeedsAdministratorOfTheRegion() {
        when(access.mayAdminister(actor, REGION)).thenReturn(false);
        org.mockito.Mockito.doCallRealMethod().when(access).requireAdminister(actor, REGION);
        var input = new AchievementService.AchievementInput(REGION, "X", null, "star", null,
                AchievementVisibility.SCOPE, DuplicateMode.MULTIPLE, null);
        assertThat(codeOf(() -> service.create(input))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(access.mayAdminister(actor, REGION)).thenReturn(true);
        when(achievements.save(any(Achievement.class))).thenAnswer(inv -> {
            Achievement a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        ArgumentCaptor<Achievement> saved = ArgumentCaptor.forClass(Achievement.class);
        var view = service.create(input);
        verify(achievements).save(saved.capture());
        assertThat(saved.getValue().getRegionId()).isEqualTo(REGION);
        assertThat(view.mayAdminister()).isTrue();
        verify(history).event(AchievementAction.DEFINED, view.achievement().getId());
    }

    @Test
    void validityDaysAreClearedBackToUnlimitedButNotTogetherWithAValue() {
        achievement.setValidityDays(365);
        var both = new AchievementService.AchievementInput(null, null, null, null, 30, null, null, null, true);
        assertThat(codeOf(() -> service.update(achievement.getId(), both))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(achievement.getValidityDays()).isEqualTo(365);

        var clear = new AchievementService.AchievementInput(null, null, null, null, null, null, null, null, true);
        service.update(achievement.getId(), clear);
        assertThat(achievement.getValidityDays()).isNull();
        verify(history).event(AchievementAction.UPDATED, achievement.getId());
        verify(history).record(any());

        // null still means unchanged
        achievement.setValidityDays(10);
        service.update(achievement.getId(),
                new AchievementService.AchievementInput(null, "Putzheld", null, null, null, null, null, null));
        assertThat(achievement.getValidityDays()).isEqualTo(10);
    }

    @Test
    void awardValidityIsClearedExplicitlyAndRecorded() {
        AwardedAchievement existing = award(LocalDate.of(2026, 12, 31));
        when(awards.findById(existing.getId())).thenReturn(Optional.of(existing));
        assertThat(codeOf(() -> service.updateAward(existing.getId(), LocalDate.of(2027, 1, 1), true, null)))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(existing.getValidUntil()).isEqualTo("2026-12-31");

        service.updateAward(existing.getId(), null, false, "nur Notiz");
        assertThat(existing.getValidUntil()).isEqualTo("2026-12-31");

        service.updateAward(existing.getId(), null, true, null);
        assertThat(existing.getValidUntil()).isNull();
        // one AWARD_EDITED entry per successful edit (the rejected one recorded nothing)
        verify(history, org.mockito.Mockito.times(2)).event(AchievementAction.AWARD_EDITED, achievement.getId());
    }

    static ApiErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (network.foodsharing.common.error.ApiException e) {
            return (ApiErrorCode) org.springframework.test.util.ReflectionTestUtils.getField(e, "code");
        }
        return null;
    }

    private AwardedAchievement award(LocalDate validUntil) {
        AwardedAchievement award = new AwardedAchievement();
        award.setId(UUID.randomUUID());
        award.setAchievementId(achievement.getId());
        award.setUserId(recipient.getId());
        award.setAwardedAt(NOW.minusSeconds(86400));
        award.setValidUntil(validUntil);
        return award;
    }
}
