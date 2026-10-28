package network.foodsharing.notification.telegram.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One-time token of the Telegram deep link {@code https://t.me/<bot>?start=<token>} (A28, 15 min). */
@Getter
@Setter
@Entity
@Table(name = "telegram_link_token")
public class TelegramLinkToken extends BaseEntity {

    @Column(name = "token", nullable = false, length = 64, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
