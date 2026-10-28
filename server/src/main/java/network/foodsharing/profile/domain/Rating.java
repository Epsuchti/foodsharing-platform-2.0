package network.foodsharing.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A trust banana (upstream {@code fs_rating}, G4): one per sender and recipient. */
@Getter
@Setter
@Entity
@Table(name = "rating")
public class Rating extends BaseEntity {

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "message", nullable = false, length = 5000)
    private String message;
}
