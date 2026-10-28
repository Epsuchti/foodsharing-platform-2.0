package network.foodsharing.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A co-author may edit a survey and read its results. */
@Getter
@Setter
@Entity
@Table(name = "survey_coauthor")
public class SurveyCoauthor extends BaseEntity {

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
