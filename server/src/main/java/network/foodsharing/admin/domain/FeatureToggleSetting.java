package network.foodsharing.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Stored state of a {@link FeatureToggle} (A43). */
@Getter
@Setter
@Entity
@Table(name = "feature_toggle")
public class FeatureToggleSetting extends BaseEntity {

    @Column(name = "toggle_key", nullable = false, length = 80)
    private String toggleKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "description", length = 500)
    private String description;
}
