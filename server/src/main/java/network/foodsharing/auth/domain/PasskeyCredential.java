package network.foodsharing.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * A registered passkey (WebAuthn credential record). Binary values are stored base64url-encoded; {@code transports}
 * is a comma-separated list. The attestation object is kept because Spring Security re-reads the attested credential
 * data from it on every authentication.
 */
@Getter
@Setter
@Entity
@Table(name = "passkey_credential")
public class PasskeyCredential extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    private String credentialId;

    @Column(name = "public_key_cose", nullable = false, length = 4096)
    private String publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "transports", length = 100)
    private String transports;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "credential_type", nullable = false, length = 40)
    private String credentialType;

    @Column(name = "uv_initialized", nullable = false)
    private boolean uvInitialized;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @Column(name = "attestation_object", length = 16384)
    private String attestationObject;

    @Column(name = "attestation_client_data_json", length = 8192)
    private String attestationClientDataJson;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
