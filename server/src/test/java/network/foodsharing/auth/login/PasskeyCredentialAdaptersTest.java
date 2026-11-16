package network.foodsharing.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.auth.domain.PasskeyUserEntity;
import network.foodsharing.auth.service.PasskeyCredentialData;
import network.foodsharing.auth.service.PasskeyService;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;

/**
 * The Spring Security WebAuthn repositories over {@link PasskeyService}: every credential field survives the trip
 * through the base64url columns, user handles map to platform user ids (was PasskeyRepositoriesIT).
 */
class PasskeyCredentialAdaptersTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");

    private final PasskeyService passkeys = mock(PasskeyService.class);
    private final JpaUserCredentialRepository credentials = new JpaUserCredentialRepository(passkeys);
    private final JpaPublicKeyCredentialUserEntityRepository userEntities =
            new JpaPublicKeyCredentialUserEntityRepository(passkeys);

    @Test
    void everyCredentialFieldRoundTripsThroughTheStoredColumns() {
        UUID userId = UUID.randomUUID();
        Bytes handle = Bytes.random();
        PasskeyUserEntity owner = owner(userId, handle);
        CredentialRecord record = ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(Bytes.random())
                .userEntityUserId(handle)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3, 4, 5}))
                .signatureCount(7)
                .uvInitialized(true)
                .transports(Set.of(AuthenticatorTransport.USB, AuthenticatorTransport.HYBRID))
                .backupEligible(true)
                .backupState(false)
                .attestationObject(Bytes.random())
                .attestationClientDataJSON(Bytes.random())
                .label("YubiKey")
                .build();

        credentials.save(record);
        ArgumentCaptor<PasskeyCredentialData> data = ArgumentCaptor.forClass(PasskeyCredentialData.class);
        verify(passkeys).saveCredential(data.capture());
        assertThat(data.getValue().userHandle()).isEqualTo(handle.toBase64UrlString());
        assertThat(data.getValue().transports()).isEqualTo("hybrid,usb");

        PasskeyCredential stored = stored(userId, data.getValue());
        when(passkeys.findCredential(record.getCredentialId().toBase64UrlString())).thenReturn(Optional.of(stored));
        when(passkeys.findUserEntityByUserId(userId)).thenReturn(Optional.of(owner));
        when(passkeys.findUserEntityByHandle(handle.toBase64UrlString())).thenReturn(Optional.of(owner));
        when(passkeys.findCredentialsByHandle(handle.toBase64UrlString())).thenReturn(List.of(stored));

        CredentialRecord loaded = credentials.findByCredentialId(record.getCredentialId());
        assertThat(loaded.getCredentialId()).isEqualTo(record.getCredentialId());
        assertThat(loaded.getUserEntityUserId()).isEqualTo(handle);
        assertThat(loaded.getPublicKey().getBytes()).isEqualTo(record.getPublicKey().getBytes());
        assertThat(loaded.getSignatureCount()).isEqualTo(7);
        assertThat(loaded.getTransports()).containsExactlyInAnyOrder(AuthenticatorTransport.USB,
                AuthenticatorTransport.HYBRID);
        assertThat(loaded.getCredentialType()).isEqualTo(PublicKeyCredentialType.PUBLIC_KEY);
        assertThat(loaded.isUvInitialized()).isTrue();
        assertThat(loaded.isBackupEligible()).isTrue();
        assertThat(loaded.isBackupState()).isFalse();
        assertThat(loaded.getAttestationObject()).isEqualTo(record.getAttestationObject());
        assertThat(loaded.getAttestationClientDataJSON()).isEqualTo(record.getAttestationClientDataJSON());
        assertThat(loaded.getLabel()).isEqualTo("YubiKey");
        assertThat(loaded.getCreated()).isEqualTo(CREATED);
        assertThat(loaded.getLastUsed()).as("never used: created").isEqualTo(CREATED);
        assertThat(credentials.findByUserId(handle)).hasSize(1);
        assertThat(credentials.findByUserId(Bytes.random())).isEmpty();
        assertThat(credentials.findByCredentialId(Bytes.random())).isNull();

        stored.setLastUsedAt(CREATED.plusSeconds(60));
        assertThat(credentials.findByCredentialId(record.getCredentialId()).getLastUsed())
                .isEqualTo(CREATED.plusSeconds(60));
    }

    @Test
    void transportsAreStoredSortedAndParsedLeniently() {
        assertThat(JpaUserCredentialRepository.transports(Set.of())).isNull();
        assertThat(JpaUserCredentialRepository.transports(null)).isNull();
        assertThat(JpaUserCredentialRepository.parseTransports(null)).isEmpty();
        assertThat(JpaUserCredentialRepository.parseTransports(" usb , ,nfc")).containsExactly(
                AuthenticatorTransport.USB, AuthenticatorTransport.NFC);
    }

    @Test
    void credentialsAreNeverDeletedThroughSpringSecurity() {
        assertThatThrownBy(() -> credentials.delete(Bytes.random())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> userEntities.delete(Bytes.random())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void userEntitiesAreKeyedByThePlatformUserIdAndShowTheDisplayName() {
        UUID userId = UUID.randomUUID();
        Bytes handle = Bytes.random();
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(handle).name(userId.toString()).displayName("ignored").build());
        verify(passkeys).saveUserEntity(userId, handle.toBase64UrlString());

        PasskeyUserEntity owner = owner(userId, handle);
        User user = new User();
        user.setDisplayName("Passkey Person");
        when(passkeys.findUserEntityByUserId(userId)).thenReturn(Optional.of(owner));
        when(passkeys.findUserEntityByHandle(handle.toBase64UrlString())).thenReturn(Optional.of(owner));
        when(passkeys.findActiveUser(userId)).thenReturn(Optional.of(user));

        PublicKeyCredentialUserEntity byName = userEntities.findByUsername(userId.toString());
        assertThat(byName.getId()).isEqualTo(handle);
        assertThat(byName.getName()).isEqualTo(userId.toString());
        assertThat(byName.getDisplayName()).isEqualTo("Passkey Person");
        assertThat(userEntities.findById(handle).getName()).isEqualTo(userId.toString());
        assertThat(userEntities.findByUsername("not-a-uuid")).isNull();
        assertThat(userEntities.findById(Bytes.random())).isNull();

        when(passkeys.findActiveUser(userId)).thenReturn(Optional.empty());
        assertThat(userEntities.findById(handle).getDisplayName()).as("inactive user: the id")
                .isEqualTo(userId.toString());

        assertThatThrownBy(() -> userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random()).name("not-a-uuid").displayName("x").build()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(passkeys, times(1)).saveUserEntity(any(), any());
    }

    private static PasskeyUserEntity owner(UUID userId, Bytes handle) {
        PasskeyUserEntity owner = new PasskeyUserEntity();
        owner.setUserId(userId);
        owner.setHandle(handle.toBase64UrlString());
        return owner;
    }

    /** What PasskeyService.saveCredential stores for the data. */
    private static PasskeyCredential stored(UUID userId, PasskeyCredentialData data) {
        PasskeyCredential credential = new PasskeyCredential();
        credential.setId(UUID.randomUUID());
        credential.setCreatedAt(CREATED);
        credential.setUserId(userId);
        credential.setCredentialId(data.credentialId());
        credential.setPublicKeyCose(data.publicKeyCose());
        credential.setSignatureCount(data.signatureCount());
        credential.setTransports(data.transports());
        credential.setLabel(data.label());
        credential.setCredentialType(data.credentialType());
        credential.setUvInitialized(data.uvInitialized());
        credential.setBackupEligible(data.backupEligible());
        credential.setBackupState(data.backupState());
        credential.setAttestationObject(data.attestationObject());
        credential.setAttestationClientDataJson(data.attestationClientDataJson());
        return credential;
    }
}
