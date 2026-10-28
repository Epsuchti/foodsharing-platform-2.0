package network.foodsharing.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A request for mediation sent to the mediation team of a region (G2). */
@Getter
@Setter
@Entity
@Table(name = "mediation_request")
public class MediationRequest extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "counterpart_user_id")
    private UUID counterpartUserId;

    @Column(name = "message", nullable = false, length = 4000)
    private String message;
}
