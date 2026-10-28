package network.foodsharing.achievement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Name and description of an achievement in one language (A14). */
@Getter
@Setter
@Entity
@Table(name = "achievement_i18n")
public class AchievementI18n extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;
}
