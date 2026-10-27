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
import network.foodsharing.security.WorkingGroupFunction;

/** The working group that holds a function in a region. */
@Getter
@Setter
@Entity
@Table(name = "region_function")
public class RegionFunction extends BaseEntity {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "function_key", nullable = false, length = 40)
    private WorkingGroupFunction function;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;
}
