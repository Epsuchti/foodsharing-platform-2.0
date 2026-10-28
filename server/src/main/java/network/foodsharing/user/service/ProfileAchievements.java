package network.foodsharing.user.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Achievements shown on a profile (B2; implemented by the achievement module in B9). Default: none. */
public interface ProfileAchievements {

    record Achievement(UUID id, String name, String icon, Instant awardedAt, LocalDate validUntil,
                       String regionName) {
    }

    /** Awards of {@code userId} the viewer may see. */
    List<Achievement> visibleTo(UUID viewerId, UUID userId);
}
