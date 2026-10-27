package network.foodsharing.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Question text in one language. */
@Getter
@Setter
@Entity
@Table(name = "quiz_question_i18n")
public class QuizQuestionI18n extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;
}
