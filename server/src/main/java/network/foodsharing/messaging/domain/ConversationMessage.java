package network.foodsharing.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import java.time.Instant;
import java.util.UUID;

/** A message; {@link #senderId} {@code null} = system message ("foodsharing"). */
@Getter
@Setter
@Entity
@Table(name = "message")
public class ConversationMessage extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "seq", nullable = false)
    private long seq;

    @Column(name = "sender_id")
    private UUID senderId;

    @Column(name = "body", nullable = false, length = 32600)
    private String body;

    @Column(name = "client_key", length = 64)
    private String clientKey;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
