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

/** One attempt at a quiz. {@code questions} is the JSON snapshot of the asked questions (texts in the session language, answers with ratings), {@code results} the JSON list of given answers. */
@Getter
@Setter
@Entity
@Table(name = "quiz_session")
public class QuizSession extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private QuizSessionStatus status;

    @Column(name = "questions", nullable = false, length = 32600)
    private String questions;

    @Column(name = "results", length = 32600)
    private String results;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "question_started_at")
    private Instant questionStartedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "failure_points", nullable = false, precision = 6, scale = 2)
    private java.math.BigDecimal failurePoints = java.math.BigDecimal.ZERO;

    @Column(name = "max_failure_points", nullable = false)
    private int maxFailurePoints;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "timed", nullable = false)
    private boolean timed;

    @Column(name = "test", nullable = false)
    private boolean test;

    @Column(name = "language", nullable = false, length = 5)
    private String language;
}
