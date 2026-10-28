package network.foodsharing.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One user's submission (once per survey); {@code storeId} from the link for surveys with a STORE question. */
@Getter
@Setter
@Entity
@Table(name = "survey_response")
public class SurveyResponse extends BaseEntity {

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "store_id")
    private UUID storeId;
}
