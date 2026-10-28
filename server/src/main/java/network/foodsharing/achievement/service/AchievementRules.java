package network.foodsharing.achievement.service;

import java.time.LocalDate;
import network.foodsharing.achievement.domain.AchievementVisibility;

/**
 * Pure achievement rules (AC1, upstream {@code AchievementPermissions} / {@code AchievementTransactions}).
 * <ul>
 *   <li>Administer (define, edit, delete, award, revoke) = ORGA, or admin of the achievement's region; global
 *       achievements are ORGA only.</li>
 *   <li>Nobody awards themself (upstream {@code mayAwardAchievement}).</li>
 *   <li>Award visibility for a viewer: administrators see everything; HIDDEN nobody else; the recipient sees the
 *       rest; PRIVATE only administrators; STORE_MANAGERS viewers with role ≥ STORE_MANAGER who are members of the
 *       region; SCOPE members of the region; GLOBAL everybody. For global achievements every member counts as a
 *       member of the scope.</li>
 *   <li>Definitions: administrators see all, members of the region all but HIDDEN ones.</li>
 *   <li>Validity: an explicit date wins, else {@code awardDay + validityDays}, else unlimited (null). OVERRIDE
 *       keeps one active award and extends it to the later end (null = unlimited wins).</li>
 * </ul>
 */
public final class AchievementRules {

    /** What the viewer is with respect to one award / achievement. */
    public record Viewer(boolean admin, boolean member, boolean storeManager, boolean recipient) {
    }

    private AchievementRules() {
    }

    public static boolean awardVisible(AchievementVisibility visibility, Viewer viewer) {
        if (viewer.admin()) {
            return true;
        }
        if (visibility == AchievementVisibility.HIDDEN) {
            return false;
        }
        if (viewer.recipient()) {
            return true;
        }
        return switch (visibility) {
            case PRIVATE, HIDDEN -> false;
            case STORE_MANAGERS -> viewer.storeManager() && viewer.member();
            case SCOPE -> viewer.member();
            case GLOBAL -> true;
        };
    }

    public static boolean definitionVisible(AchievementVisibility visibility, Viewer viewer) {
        return viewer.admin() || viewer.member() && visibility != AchievementVisibility.HIDDEN;
    }

    public static boolean mayAward(boolean admin, boolean self) {
        return admin && !self;
    }

    public static LocalDate validUntil(LocalDate awardDay, Integer validityDays, LocalDate requested) {
        if (requested != null) {
            return requested;
        }
        return validityDays == null ? null : awardDay.plusDays(validityDays);
    }

    /** OVERRIDE: the later of both ends; null (unlimited) wins. */
    public static LocalDate extended(LocalDate current, LocalDate fresh) {
        if (current == null || fresh == null) {
            return null;
        }
        return fresh.isAfter(current) ? fresh : current;
    }
}
