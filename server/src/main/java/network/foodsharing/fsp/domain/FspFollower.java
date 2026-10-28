package network.foodsharing.fsp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A follower or manager of a food share point (upstream fs_fairteiler_follower). */
@Getter
@Setter
@Entity
@Table(name = "fsp_follower")
public class FspFollower extends BaseEntity {

    @Column(name = "fsp_id", nullable = false)
    private UUID fspId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 40)
    private FspFollowerRole role = FspFollowerRole.FOLLOWER;

    @Column(name = "bell", nullable = false)
    private boolean bell = true;

    @Column(name = "mail", nullable = false)
    private boolean mail;
}
