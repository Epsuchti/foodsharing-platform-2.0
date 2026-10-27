package network.foodsharing.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A kind of food a store gives. */
@Getter
@Setter
@Entity
@Table(name = "grocery")
public class Grocery extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;
}
