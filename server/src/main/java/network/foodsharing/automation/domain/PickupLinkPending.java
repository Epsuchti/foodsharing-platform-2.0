package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * A sign-up ({@code storeId}, {@code pickupAt}, {@code userId}) whose linked-store pickup in {@code linkedStoreId} was
 * signed out (decision 167 S3, Q4): until {@code deadlineAt} the person may book another one, then the
 * {@code automation-evaluate} job frees the slot. Gone once the link is satisfied again or the sign-up ends.
 */
@Getter
@Setter
@Entity
@Table(name = "pickup_link_pending")
public class PickupLinkPending extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "linked_store_id", nullable = false)
    private UUID linkedStoreId;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;
}
