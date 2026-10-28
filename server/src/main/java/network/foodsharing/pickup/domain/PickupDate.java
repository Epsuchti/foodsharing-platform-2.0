package network.foodsharing.pickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * A one-time pickup or the override of a regular occurrence (table {@code pickup}, upstream fs_fetchdate); slot count
 * 0 cancels the occurrence (A20). Signing up for a regular occurrence creates this row, so later schedule edits never
 * delete taken slots.
 */
@Getter
@Setter
@Entity
@Table(name = "pickup")
public class PickupDate extends BaseEntity {

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "pickup_at", nullable = false, updatable = false)
    private Instant pickupAt;

    @Column(name = "slot_count", nullable = false)
    private int slotCount;

    @Column(name = "description", length = 100)
    private String description;
}
