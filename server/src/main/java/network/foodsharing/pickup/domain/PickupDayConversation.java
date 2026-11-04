package network.foodsharing.pickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** The named group chat of a store's pickup day (PU3, decision 164): one per store and day in the store's zone. */
@Getter
@Setter
@Entity
@Table(name = "pickup_day_conversation")
public class PickupDayConversation extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
}
