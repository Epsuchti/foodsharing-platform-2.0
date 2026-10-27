package network.foodsharing.quiz.domain;

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

/** An answer of a question with its rating. */
@Getter
@Setter
@Entity
@Table(name = "quiz_answer")
public class QuizAnswer extends BaseEntity {

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private AnswerRating rating;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
