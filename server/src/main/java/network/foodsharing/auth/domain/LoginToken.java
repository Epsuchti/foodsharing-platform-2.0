package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.api.model.Language;
import network.foodsharing.common.persistence.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A magic-link token (plan §8.1): single use, short-lived; only the SHA-256 of the token is stored. */
@Getter
@Setter
@Entity
@Table(name = "login_token")
public class LoginToken extends BaseEntity {

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "return_url", length = 500)
    private String returnUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 5)
    private Language language;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
