package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Subscription to notifications about new bookable EinAbs (one per user). */
@Getter
@Setter
@Entity
@Table(name = "intro_pickup_subscription")
public class IntroPickupSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private SubscriptionChannel channel;

    @Column(name = "delivery_email", length = 320)
    private String deliveryEmail;

    @Column(name = "language", nullable = false, length = 5)
    private String language = "de";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "unsubscribe_token", nullable = false, length = 128)
    private String unsubscribeToken;
}
