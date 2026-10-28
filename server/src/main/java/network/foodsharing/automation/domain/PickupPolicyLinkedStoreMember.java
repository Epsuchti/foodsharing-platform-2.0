package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One store of a linked-store rule. */
@Getter
@Setter
@Entity
@Table(name = "pickup_policy_linked_store_member")
public class PickupPolicyLinkedStoreMember extends BaseEntity {

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;
}
