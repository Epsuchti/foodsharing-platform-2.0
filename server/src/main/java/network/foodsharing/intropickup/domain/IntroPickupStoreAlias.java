package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Store name alias of a region for the CSV import. */
@Getter
@Setter
@Entity
@Table(name = "intro_pickup_store_alias")
public class IntroPickupStoreAlias extends BaseEntity {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Column(name = "alias_key", nullable = false, length = 200)
    private String aliasKey;

    @Column(name = "store_name", nullable = false, length = 500)
    private String storeName;
}
