package network.foodsharing.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A quiz (plan §13.9 Q1); texts in {@link QuizI18n}. */
@Getter
@Setter
@Entity
@Table(name = "quiz")
public class Quiz extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40, updatable = false)
    private QuizKind kind;

    @Column(name = "max_failure_points", nullable = false)
    private int maxFailurePoints;

    @Column(name = "question_count_timed", nullable = false)
    private int questionCountTimed;

    @Column(name = "question_count_untimed", nullable = false)
    private int questionCountUntimed;
}
