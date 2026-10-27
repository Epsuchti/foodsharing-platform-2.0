package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Translation of a region onboarding step (docs/conventions.md A14). */
@Getter
@Setter
@Entity
@Table(name = "region_onboarding_step_i18n")
public class RegionOnboardingStepI18n extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;
}
