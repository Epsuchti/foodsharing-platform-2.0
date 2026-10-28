package network.foodsharing.fsp.domain;

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

/** A food share point (FS1, upstream fs_fairteiler). */
@Getter
@Setter
@Entity
@Table(name = "food_share_point")
public class FoodSharePoint extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 32600)
    private String description;

    @Column(name = "street", nullable = false, length = 200)
    private String street;

    @Column(name = "postcode", nullable = false, length = 20)
    private String postcode;

    @Column(name = "city", nullable = false, length = 200)
    private String city;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private FspStatus status = FspStatus.INACTIVE;

    @Column(name = "photo_upload_id")
    private UUID photoUploadId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
