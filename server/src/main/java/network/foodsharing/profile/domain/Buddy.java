package network.foodsharing.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.user.domain.BuddyStatus;

/** One direction of a buddy relation (upstream {@code fs_buddy}): {@code userId} asked {@code buddyId}. */
@Getter
@Setter
@Entity
@Table(name = "buddy")
public class Buddy extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "buddy_id", nullable = false)
    private UUID buddyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private BuddyStatus status;
}
