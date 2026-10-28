package network.foodsharing.pickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** The one extra slot the earliest sign-up added to a pickup (AU6). */
@Getter
@Setter
@Entity
@Table(name = "pickup_extra_slot")
public class PickupExtraSlot extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "previous_total_slots", nullable = false)
    private int previousTotalSlots;

    @Column(name = "new_total_slots", nullable = false)
    private int newTotalSlots;
}
