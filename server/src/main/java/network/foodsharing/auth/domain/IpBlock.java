package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A blocked IP address or CIDR range (parity: {@code fs_ipblock}); {@code untilAt == null} blocks forever. */
@Getter
@Setter
@Entity
@Table(name = "ip_block")
public class IpBlock extends BaseEntity {

    @Column(name = "cidr", nullable = false, length = 50)
    private String cidr;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "until_at")
    private Instant untilAt;

    public boolean isActiveAt(Instant now) {
        return untilAt == null || untilAt.isAfter(now);
    }
}
