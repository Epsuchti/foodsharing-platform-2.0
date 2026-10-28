package network.foodsharing.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One question; {@code optionsJson} is a JSON array of the option texts (choice types, else {@code []}). */
@Getter
@Setter
@Entity
@Table(name = "survey_question")
public class SurveyQuestion extends BaseEntity {

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private SurveyQuestionType type;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "options_json", nullable = false, length = 32600)
    private String optionsJson = "[]";
}
