package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A place of an EinAb; booked when it has a booking user. */
@Getter
@Setter
@Entity
@Table(name = "intro_pickup_slot")
public class IntroPickupSlot extends BaseEntity {

    @Column(name = "intro_pickup_id", nullable = false, updatable = false)
    private UUID introPickupId;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Column(name = "booking_user_id")
    private UUID bookingUserId;

    @Column(name = "booked_at")
    private Instant bookedAt;

    @Column(name = "done_at")
    private Instant doneAt;
}
