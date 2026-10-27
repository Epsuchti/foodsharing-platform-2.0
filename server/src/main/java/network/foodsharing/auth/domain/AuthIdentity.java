package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.user.domain.User;

/**
 * A login identity of a user: a Google account ({@code subject} = OIDC {@code sub}) or a verified email address
 * ({@code subject} = normalized email). Unique per (provider, subject).
 */
@Getter
@Setter
@Entity
@Table(name = "auth_identity")
public class AuthIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private IdentityProvider provider;

    @Column(name = "subject", nullable = false, length = 320)
    private String subject;

    /** Shown in the settings, e.g. the Google account's email. */
    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
