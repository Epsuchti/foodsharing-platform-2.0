package network.foodsharing.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import java.util.UUID;

/** Link region/working group → its conversation. */
@Getter
@Setter
@Entity
@Table(name = "region_conversation")
public class RegionConversation extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
}
