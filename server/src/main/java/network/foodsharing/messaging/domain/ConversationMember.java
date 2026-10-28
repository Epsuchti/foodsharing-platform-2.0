package network.foodsharing.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import java.time.Instant;
import java.util.UUID;

/** Membership with read state ({@link #lastReadSeq}) and the digest-mail marker. */
@Getter
@Setter
@Entity
@Table(name = "conversation_member")
public class ConversationMember extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq;

    @Column(name = "last_mailed_at")
    private Instant lastMailedAt;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
