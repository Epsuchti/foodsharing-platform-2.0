package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Comment of a guide or admin on a booking. */
@Getter
@Setter
@Entity
@Table(name = "intro_pickup_comment")
public class IntroPickupComment extends BaseEntity {

    @Column(name = "slot_id", nullable = false, updatable = false)
    private UUID slotId;

    @Column(name = "booking_user_id")
    private UUID bookingUserId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "admin_comment", nullable = false)
    private boolean adminComment;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;
}
