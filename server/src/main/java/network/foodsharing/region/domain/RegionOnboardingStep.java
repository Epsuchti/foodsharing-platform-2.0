package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** An extra onboarding step of a region (plan §11.1). */
@Getter
@Setter
@Entity
@Table(name = "region_onboarding_step")
public class RegionOnboardingStep extends BaseEntity {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Column(name = "step_key", nullable = false, length = 60)
    private String stepKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
