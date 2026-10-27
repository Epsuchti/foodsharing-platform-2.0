package network.foodsharing.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A key account manager of a chain. */
@Getter
@Setter
@Entity
@Table(name = "store_chain_kam")
public class StoreChainKam extends BaseEntity {

    @Column(name = "chain_id", nullable = false, updatable = false)
    private UUID chainId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
}
