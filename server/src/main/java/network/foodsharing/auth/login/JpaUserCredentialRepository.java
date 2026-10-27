package network.foodsharing.auth.login;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.auth.domain.PasskeyUserEntity;
import network.foodsharing.auth.service.PasskeyCredentialData;
import network.foodsharing.auth.service.PasskeyService;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Component;

/**
 * WebAuthn credential records backed by {@code passkey_credential} (binary values base64url). Saving a new record
 * (registration) records PASSKEY_REGISTERED; deleting goes through {@code DELETE /api/auth/passkeys/{id}} instead.
 */
@Component
public class JpaUserCredentialRepository implements UserCredentialRepository {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final PasskeyService passkeys;

    public JpaUserCredentialRepository(PasskeyService passkeys) {
        this.passkeys = passkeys;
    }

    @Override
    public void delete(Bytes credentialId) {
        throw new UnsupportedOperationException("Passkeys are deleted via DELETE /api/auth/passkeys/{id}");
    }

    @Override
    public void save(CredentialRecord record) {
        passkeys.saveCredential(new PasskeyCredentialData(
                record.getCredentialId().toBase64UrlString(),
                record.getUserEntityUserId().toBase64UrlString(),
                ENCODER.encodeToString(record.getPublicKey().getBytes()),
                record.getSignatureCount(),
                transports(record.getTransports()),
                record.getLabel(),
                record.getCredentialType() == null ? null : record.getCredentialType().getValue(),
                record.isUvInitialized(),
                record.isBackupEligible(),
                record.isBackupState(),
                record.getAttestationObject() == null ? null : record.getAttestationObject().toBase64UrlString(),
                record.getAttestationClientDataJSON() == null ? null
                        : record.getAttestationClientDataJSON().toBase64UrlString()));
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return passkeys.findCredential(credentialId.toBase64UrlString())
                .flatMap(credential -> passkeys.findUserEntityByUserId(credential.getUserId())
                        .map(owner -> toRecord(credential, owner)))
                .orElse(null);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        String handle = userId.toBase64UrlString();
        return passkeys.findUserEntityByHandle(handle)
                .map(owner -> passkeys.findCredentialsByHandle(handle).stream()
                        .map(credential -> toRecord(credential, owner))
                        .toList())
                .orElse(List.of());
    }

    static CredentialRecord toRecord(PasskeyCredential credential, PasskeyUserEntity owner) {
        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.valueOf(credential.getCredentialType()))
                .credentialId(Bytes.fromBase64(credential.getCredentialId()))
                .userEntityUserId(Bytes.fromBase64(owner.getHandle()))
                .publicKey(new ImmutablePublicKeyCose(DECODER.decode(credential.getPublicKeyCose())))
                .signatureCount(credential.getSignatureCount())
                .uvInitialized(credential.isUvInitialized())
                .transports(parseTransports(credential.getTransports()))
                .backupEligible(credential.isBackupEligible())
                .backupState(credential.isBackupState())
                .attestationObject(credential.getAttestationObject() == null ? null
                        : Bytes.fromBase64(credential.getAttestationObject()))
                .attestationClientDataJSON(credential.getAttestationClientDataJson() == null ? null
                        : Bytes.fromBase64(credential.getAttestationClientDataJson()))
                .label(credential.getLabel())
                .created(credential.getCreatedAt())
                .lastUsed(credential.getLastUsedAt() == null ? credential.getCreatedAt() : credential.getLastUsedAt())
                .build();
    }

    static String transports(Set<AuthenticatorTransport> transports) {
        if (transports == null || transports.isEmpty()) {
            return null;
        }
        return transports.stream().map(AuthenticatorTransport::getValue).sorted().collect(Collectors.joining(","));
    }

    static Set<AuthenticatorTransport> parseTransports(String transports) {
        if (transports == null || transports.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(transports.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(AuthenticatorTransport::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
