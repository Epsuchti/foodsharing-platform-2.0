package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * Linked-store rule of a pickup policy: a sign-up here needs a sign-up in one of the member stores within the window.
 * A bidirectional rule has a mirror row on every member store's policy ({@code mirrorOf} = source rule, window swapped).
 */
@Getter
@Setter
@Entity
@Table(name = "pickup_policy_linked_store")
public class PickupPolicyLinkedStore extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "bidirectional", nullable = false)
    private boolean bidirectional;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_mode", nullable = false, length = 20)
    private LinkedStoreWindowMode windowMode = LinkedStoreWindowMode.SAME_DAY;

    @Column(name = "window_days_before", nullable = false)
    private int windowDaysBefore;

    @Column(name = "window_days_after", nullable = false)
    private int windowDaysAfter;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes = 20;

    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "mirror_of")
    private UUID mirrorOf;
}
