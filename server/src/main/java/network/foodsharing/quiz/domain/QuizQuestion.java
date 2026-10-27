package network.foodsharing.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A quiz question; deleted questions stay for old session snapshots. */
@Getter
@Setter
@Entity
@Table(name = "quiz_question")
public class QuizQuestion extends BaseEntity {

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds = 60;

    @Column(name = "wiki_link", length = 500)
    private String wikiLink;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "failure_points", nullable = false)
    private int failurePoints = 1;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
