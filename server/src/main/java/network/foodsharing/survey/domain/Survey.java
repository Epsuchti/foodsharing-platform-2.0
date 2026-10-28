package network.foodsharing.survey.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A survey owned by a CAN_MANAGE_SURVEYS holder (reference einab-tabelle Survey). */
@Getter
@Setter
@Entity
@Table(name = "survey")
public class Survey extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 32600)
    private String description;
}
