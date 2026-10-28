package network.foodsharing.achievement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** An achievement awarded to a member; revocation is soft ({@code revokedAt}). */
@Getter
@Setter
@Entity
@Table(name = "awarded_achievement")
public class AwardedAchievement extends BaseEntity {

    @Column(name = "achievement_id", nullable = false, updatable = false)
    private UUID achievementId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Null when awarded automatically (SYSTEM). */
    @Column(name = "awarded_by")
    private UUID awardedBy;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "notice", length = 1000)
    private String notice;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    public boolean isActive() {
        return revokedAt == null;
    }
}
