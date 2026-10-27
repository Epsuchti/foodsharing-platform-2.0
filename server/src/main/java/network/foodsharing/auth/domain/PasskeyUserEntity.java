package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * The WebAuthn user handle of a user (random, base64url, never the user id), created on the first passkey
 * registration.
 */
@Getter
@Setter
@Entity
@Table(name = "passkey_user_entity")
public class PasskeyUserEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "handle", nullable = false, unique = true, length = 128)
    private String handle;
}
