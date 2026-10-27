package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * A blocked email pattern (parity: email blocklist). {@code mailinator.com} blocks the domain and its subdomains,
 * {@code *} is a wildcard ({@code *.example.org}, {@code trash*.com}), a pattern with {@code @} matches the whole
 * address.
 */
@Getter
@Setter
@Entity
@Table(name = "email_blocklist")
public class EmailBlocklistEntry extends BaseEntity {

    @Column(name = "pattern", nullable = false, unique = true, length = 320)
    private String pattern;

    @Column(name = "reason", length = 500)
    private String reason;
}
