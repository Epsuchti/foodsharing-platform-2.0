package network.foodsharing.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A wallet pass of the digital passport. */
@Getter
@Setter
@Entity
@Table(name = "wallet_pass")
public class WalletPass extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 10)
    private WalletType walletType;

    @Column(name = "serial", nullable = false, length = 64)
    private String serial;

    @Column(name = "auth_token", length = 64)
    private String authToken;

    @Column(name = "object_id", length = 200)
    private String objectId;

    @Column(name = "last_rendered_at")
    private Instant lastRenderedAt;
}
