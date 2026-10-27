package network.foodsharing.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Comment of a quiz editor on a question. */
@Getter
@Setter
@Entity
@Table(name = "quiz_question_comment")
public class QuizQuestionComment extends BaseEntity {

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;
}
