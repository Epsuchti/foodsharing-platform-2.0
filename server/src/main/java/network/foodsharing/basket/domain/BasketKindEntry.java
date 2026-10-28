package network.foodsharing.basket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A kind of a basket (upstream fs_basket_has_types). */
@Getter
@Setter
@Entity
@Table(name = "basket_kind")
public class BasketKindEntry extends BaseEntity {

    @Column(name = "basket_id", nullable = false)
    private UUID basketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40)
    private BasketKind kind;
}
