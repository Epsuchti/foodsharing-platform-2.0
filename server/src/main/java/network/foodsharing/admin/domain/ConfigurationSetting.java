package network.foodsharing.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Stored value of a {@link ConfigurationKey} (A43). */
@Getter
@Setter
@Entity
@Table(name = "configuration")
public class ConfigurationSetting extends BaseEntity {

    @Column(name = "config_key", nullable = false, length = 120)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 4000)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;
}
