package network.foodsharing.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A user's request for a (new) passport. */
@Getter
@Setter
@Entity
@Table(name = "passport_request")
public class PassportRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "fulfilled_by")
    private UUID fulfilledBy;
}
