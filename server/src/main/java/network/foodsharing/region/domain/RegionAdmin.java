package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Ambassador of a region / admin of a working group (parity fs_botschafter). */
@Getter
@Setter
@Entity
@Table(name = "region_admin")
public class RegionAdmin extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;
}
