package network.foodsharing.onboarding.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A learning chapter read by a user. */
@Getter
@Setter
@Entity
@Table(name = "learning_progress")
public class LearningProgress extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "chapter_key", nullable = false, length = 60)
    private String chapterKey;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
