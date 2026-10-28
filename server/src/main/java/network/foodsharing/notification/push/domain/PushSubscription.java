package network.foodsharing.notification.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A browser's web push subscription (upstream fs_push_notification_subscription), unique by endpoint. */
@Getter
@Setter
@Entity
@Table(name = "push_subscription")
public class PushSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, length = 2000)
    private String endpoint;

    /** SHA-256 hex of the endpoint (unique; the endpoint itself is too long for an index). */
    @Column(name = "endpoint_hash", nullable = false, length = 64, unique = true)
    private String endpointHash;

    @Column(name = "p256dh", nullable = false, length = 200)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 100)
    private String auth;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;
}
