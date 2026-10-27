package network.foodsharing.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Answer text and explanation in one language. */
@Getter
@Setter
@Entity
@Table(name = "quiz_answer_i18n")
public class QuizAnswerI18n extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    @Column(name = "explanation", length = 4000)
    private String explanation;
}
