package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One region option value (stored as string). */
@Getter
@Setter
@Entity
@Table(name = "region_option")
public class RegionOption extends BaseEntity {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_key", nullable = false, length = 60)
    private RegionOptionKey optionKey;

    @Column(name = "option_value", length = 500)
    private String optionValue;
}
