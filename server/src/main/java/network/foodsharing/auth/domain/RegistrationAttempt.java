package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One sign-up attempt (parity: foodsharing {@code registration_attempt}); email and IP are stored hashed only. */
@Getter
@Setter
@Entity
@Table(name = "registration_attempt")
public class RegistrationAttempt extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private RegistrationOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private IdentityProvider provider;
}
