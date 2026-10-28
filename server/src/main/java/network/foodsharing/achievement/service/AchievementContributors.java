package network.foodsharing.achievement.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.achievement.domain.AchievementSystemKey;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.domain.AwardedAchievement;
import network.foodsharing.achievement.repository.AchievementRepository;
import network.foodsharing.achievement.repository.AwardedAchievementRepository;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.quiz.service.HygieneCertificates;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import network.foodsharing.user.service.ProfileAchievements;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** SPI implementations of the achievement module (A40 account data, history access, profile, hygiene expiry). */
final class AchievementContributors {

    private AchievementContributors() {
    }

    /** A40: awards are personal data and are removed on deletion; the export lists them. */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final EntityManager em;
        private final AwardedAchievementRepository awards;

        AccountData(EntityManager em, AwardedAchievementRepository awards) {
            this.em = em;
            this.awards = awards;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            awards.deleteByUser(userId);
        }

        @Override
        public String section() {
            return "achievements";
        }

        @Override
        public Object export(UUID userId) {
            return UserRows.read(em, "AwardedAchievement", "userId", userId, "achievementId", "awardedAt",
                    "validUntil", "notice", "revokedAt");
        }
    }

    /**
     * ACHIEVEMENT history: administrators of the achievement (ORGA for global ones); members of the region for
     * SCOPE / GLOBAL achievements.
     */
    @Component
    static class HistoryAccess implements HistoryAccessRule {

        private final AchievementRepository achievements;
        private final AchievementPermissionService access;

        HistoryAccess(AchievementRepository achievements, AchievementPermissionService access) {
            this.achievements = achievements;
            this.access = access;
        }

        @Override
        public SubjectType subjectType() {
            return SubjectType.ACHIEVEMENT;
        }

        @Override
        public boolean canRead(SessionPrincipal actor, UUID subjectId) {
            return achievements.findById(subjectId).map(a -> access.mayAdminister(actor, a)
                    || (a.getVisibility() == AchievementVisibility.SCOPE
                    || a.getVisibility() == AchievementVisibility.GLOBAL)
                    && a.getRegionId() != null && access.isMember(actor, a.getRegionId())).orElse(false);
        }
    }

    /** B2 profile SPI: awards visible to the viewer. */
    @Component
    static class Profile implements ProfileAchievements {

        private final AchievementService service;
        private final UserService users;

        Profile(AchievementService service, UserService users) {
            this.service = service;
            this.users = users;
        }

        @Override
        @Transactional(readOnly = true)
        public List<Achievement> visibleTo(UUID viewerId, UUID userId) {
            SessionPrincipal viewer = users.findActive(viewerId).map(AchievementPermissionService::principalOf)
                    .orElse(null);
            if (viewer == null) {
                return List.of();
            }
            return service.profileAwards(viewer, userId).stream().map(view -> new Achievement(view.award().getId(),
                    view.name(), view.achievement().getIcon(), view.award().getAwardedAt(),
                    view.award().getValidUntil(), view.regionName())).toList();
        }
    }

    /** B9: the hygiene expiry job reads the HYGIENE_CERTIFICATE award. */
    @Component
    static class Hygiene implements HygieneCertificates {

        private final AchievementService service;

        Hygiene(AchievementService service) {
            this.service = service;
        }

        @Override
        public Optional<LocalDate> validUntil(UUID userId) {
            return service.activeSystemAward(AchievementSystemKey.HYGIENE_CERTIFICATE, userId)
                    .map(AwardedAchievement::getValidUntil);
        }
    }
}
