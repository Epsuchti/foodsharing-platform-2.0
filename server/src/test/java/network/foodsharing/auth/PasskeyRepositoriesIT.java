package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.login.JpaPublicKeyCredentialUserEntityRepository;
import network.foodsharing.auth.login.JpaUserCredentialRepository;
import network.foodsharing.auth.service.AuthMethodsService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.PostgresContainerConfig;
import network.foodsharing.testsupport.RecordingMailTransport;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * The JPA-backed Spring Security WebAuthn repositories (round trip of every credential field, user handles) and the
 * passkey part of the login-methods service (rename, delete with last-method guard, history).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RecordingMailTransport.Config.class})
class PasskeyRepositoriesIT {

    @Autowired
    JpaPublicKeyCredentialUserEntityRepository userEntities;

    @Autowired
    JpaUserCredentialRepository credentials;

    @Autowired
    AuthMethodsService methods;

    @Autowired
    UserService userService;

    @Autowired
    HistoryEntryRepository historyEntries;

    @AfterEach
    void logout() {
        TestAuth.clear();
    }

    @Test
    void userEntitiesAndCredentialsRoundTrip() {
        User user = newUser();
        Bytes handle = Bytes.random();
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(handle).name(user.getId().toString()).displayName(user.getId().toString()).build());
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random()).name(user.getId().toString()).displayName("ignored").build());

        PublicKeyCredentialUserEntity byName = userEntities.findByUsername(user.getId().toString());
        assertThat(byName.getId()).isEqualTo(handle);
        assertThat(byName.getDisplayName()).isEqualTo("Passkey Person");
        assertThat(userEntities.findById(handle).getName()).isEqualTo(user.getId().toString());
        assertThat(userEntities.findByUsername("not-a-uuid")).isNull();
        assertThat(userEntities.findById(Bytes.random())).isNull();

        CredentialRecord record = record(handle, "YubiKey");
        assertThatThrownBy(() -> credentials.save(record)).isInstanceOf(ApiException.class);

        TestAuth.login(principal(user));
        credentials.save(record);

        CredentialRecord loaded = credentials.findByCredentialId(record.getCredentialId());
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
        assertThat(credentials.findByUserId(handle)).hasSize(1);
        assertThat(credentials.findByUserId(Bytes.random())).isEmpty();
        assertThat(actions(user.getId())).contains("PASSKEY_REGISTERED");

        TestAuth.clear();
        credentials.save(ImmutableCredentialRecord.fromCredentialRecord(loaded).signatureCount(8)
                .lastUsed(Instant.now()).build());
        CredentialRecord used = credentials.findByCredentialId(record.getCredentialId());
        assertThat(used.getSignatureCount()).isEqualTo(8);
        assertThat(used.getLastUsed()).isEqualTo(FixedClockConfig.SEED_NOW);
    }

    @Test
    void passkeysCanBeRenamedAndDeletedButNeverTheLastLoginMethod() {
        User user = newUser();
        Bytes handle = Bytes.random();
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(handle).name(user.getId().toString()).displayName("x").build());
        TestAuth.login(principal(user));
        credentials.save(record(handle, "Phone"));
        credentials.save(record(handle, "  "));

        List<UUID> ids = methods.list().passkeys().stream().map(passkey -> passkey.getId()).toList();
        assertThat(methods.list().passkeys()).extracting("label").containsExactlyInAnyOrder("Phone", "Passkey");

        assertThat(methods.renamePasskey(ids.get(0), "  Laptop ").getLabel()).isEqualTo("Laptop");
        methods.deletePasskey(ids.get(0));
        assertThatThrownBy(() -> methods.deletePasskey(ids.get(1)))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.LAST_LOGIN_METHOD_CANNOT_BE_REMOVED));
        assertThatThrownBy(() -> methods.renamePasskey(UUID.randomUUID(), "x"))
                .isInstanceOfSatisfying(ApiException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ApiErrorCode.PASSKEY_NOT_FOUND));
        assertThat(actions(user.getId())).contains("PASSKEY_REGISTERED", "PASSKEY_RENAMED", "PASSKEY_DELETED");
    }

    private User newUser() {
        return userService.create(new NewUser("passkey." + UUID.randomUUID() + "@example.org",
                FixedClockConfig.SEED_NOW, "Passkey Person", null, null, Language.EN, null));
    }

    private static SessionPrincipal principal(User user) {
        return new SessionPrincipal(user.getId(), UUID.randomUUID(), user.getRole(), user.getEmail());
    }

    private static CredentialRecord record(Bytes handle, String label) {
        return ImmutableCredentialRecord.builder()
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
                .label(label)
                .build();
    }

    private List<String> actions(UUID userId) {
        return historyEntries.findAll().stream()
                .filter(entry -> userId.equals(entry.getSubjectId()))
                .map(HistoryEntry::getAction)
                .toList();
    }
}
