package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * A requested email change (upstream {@code fs_mailchange}): confirmed with the token mailed to the new address,
 * aborted with the token mailed to the old one. Only SHA-256 hashes of both tokens are stored.
 */
@Getter
@Setter
@Entity
@Table(name = "email_change")
public class EmailChange extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "old_email", nullable = false, length = 320)
    private String oldEmail;

    @Column(name = "new_email", nullable = false, length = 320)
    private String newEmail;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "abort_token_hash", nullable = false, unique = true, length = 64)
    private String abortTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "aborted_at")
    private Instant abortedAt;

    public boolean isPendingAt(Instant now) {
        return completedAt == null && abortedAt == null && expiresAt.isAfter(now);
    }
}
