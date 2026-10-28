package network.foodsharing.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import java.time.Instant;
import java.util.UUID;

/** A conversation (A33): members in {@link ConversationMember}, messages numbered by {@link #lastSeq}. */
@Getter
@Setter
@Entity
@Table(name = "conversation")
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40)
    private ConversationKind kind;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 300)
    private String lastMessagePreview;

    @Column(name = "last_sender_id")
    private UUID lastSenderId;
}
