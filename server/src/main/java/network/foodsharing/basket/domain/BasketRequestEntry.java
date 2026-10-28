package network.foodsharing.basket.domain;

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

/** A request for a basket, one per requester (upstream fs_basket_anfrage). */
@Getter
@Setter
@Entity
@Table(name = "basket_request")
public class BasketRequestEntry extends BaseEntity {

    @Column(name = "basket_id", nullable = false)
    private UUID basketId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private BasketRequestStatus status = BasketRequestStatus.REQUESTED;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "answered_at")
    private Instant answeredAt;
}
