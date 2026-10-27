package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A postcode belonging to a region (region suggestions). */
@Getter
@Setter
@Entity
@Table(name = "region_postcode")
public class RegionPostcode extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "postcode", nullable = false, length = 10)
    private String postcode;
}
