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

/** A food basket (B1, upstream fs_basket). */
@Getter
@Setter
@Entity
@Table(name = "basket")
public class Basket extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private BasketStatus status = BasketStatus.ACTIVE;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 40)
    private BasketLocationType locationType;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    @Column(name = "contact_message", nullable = false)
    private boolean contactMessage;

    @Column(name = "contact_phone", nullable = false)
    private boolean contactPhone;

    @Column(name = "contact_mobile", nullable = false)
    private boolean contactMobile;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "mobile", length = 40)
    private String mobile;

    @Column(name = "lifetime_days", nullable = false)
    private int lifetimeDays;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "taken_at")
    private Instant takenAt;

    @Column(name = "photo_upload_id")
    private UUID photoUploadId;

    @Column(name = "request_count", nullable = false)
    private int requestCount;
}
