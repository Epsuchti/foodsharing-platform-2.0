package network.foodsharing.basket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A grocery of a basket (upstream fs_basket_has_art). */
@Getter
@Setter
@Entity
@Table(name = "basket_grocery")
public class BasketGrocery extends BaseEntity {

    @Column(name = "basket_id", nullable = false)
    private UUID basketId;

    @Column(name = "grocery_id", nullable = false)
    private UUID groceryId;
}
