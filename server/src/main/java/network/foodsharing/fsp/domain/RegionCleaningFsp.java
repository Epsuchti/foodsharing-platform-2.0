package network.foodsharing.fsp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A Fairteiler that counts for the cleaning rule of its region (FS2, AU1). */
@Getter
@Setter
@Entity
@Table(name = "region_cleaning_fsp")
public class RegionCleaningFsp extends BaseEntity {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Column(name = "fsp_id", nullable = false, updatable = false)
    private UUID fspId;
}
