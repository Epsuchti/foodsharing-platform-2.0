package network.foodsharing.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import java.util.UUID;

/** Link store → team (TEAM) or jumper (JUMPER) chat. */
@Getter
@Setter
@Entity
@Table(name = "store_conversation")
public class StoreConversation extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private StoreChat kind;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
}
