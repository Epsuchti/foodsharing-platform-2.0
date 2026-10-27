package network.foodsharing.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A grocery of a store. */
@Getter
@Setter
@Entity
@Table(name = "store_grocery")
public class StoreGrocery extends BaseEntity {

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "grocery_id", nullable = false, updatable = false)
    private UUID groceryId;
}
