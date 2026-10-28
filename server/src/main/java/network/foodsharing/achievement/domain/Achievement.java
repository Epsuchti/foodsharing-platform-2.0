package network.foodsharing.achievement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** An achievement definition (AC1, upstream {@code fs_achievement}); {@code regionId} null = global. */
@Getter
@Setter
@Entity
@Table(name = "achievement")
public class Achievement extends BaseEntity {

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "icon", nullable = false, length = 100)
    private String icon;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 40)
    private AchievementVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_mode", nullable = false, length = 40)
    private DuplicateMode duplicateMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", length = 40)
    private AchievementSystemKey systemKey;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
