package network.foodsharing.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** The answer to one question; {@code valuesJson} is a JSON array of strings. */
@Getter
@Setter
@Entity
@Table(name = "survey_answer")
public class SurveyAnswer extends BaseEntity {

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "values_json", nullable = false, length = 32600)
    private String valuesJson;
}
