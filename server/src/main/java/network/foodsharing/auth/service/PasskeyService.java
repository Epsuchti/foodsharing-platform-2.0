package network.foodsharing.auth.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.auth.domain.PasskeyUserEntity;
import network.foodsharing.auth.repository.PasskeyCredentialRepository;
import network.foodsharing.auth.repository.PasskeyUserEntityRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence behind Spring Security WebAuthn ({@code auth.login.Jpa*Repository} adapters): WebAuthn user handles
 * and credential records. A new credential can only be stored for the signed-in user (registration needs an
 * {@code fs_session}) and records PASSKEY_REGISTERED; updates after a login only move the signature counter.
 */
@Service
public class PasskeyService {

    public static final String DEFAULT_LABEL = "Passkey";

    private final PasskeyUserEntityRepository userEntities;
    private final PasskeyCredentialRepository credentials;
    private final UserService userService;
    private final CurrentActorService currentActorService;
    private final HistoryService history;
    private final Clock clock;

    public PasskeyService(PasskeyUserEntityRepository userEntities, PasskeyCredentialRepository credentials,
                          UserService userService, CurrentActorService currentActorService, HistoryService history,
                          Clock clock) {
        this.userEntities = userEntities;
        this.credentials = credentials;
        this.userService = userService;
        this.currentActorService = currentActorService;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<PasskeyUserEntity> findUserEntityByUserId(UUID userId) {
        return userEntities.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<PasskeyUserEntity> findUserEntityByHandle(String handle) {
        return userEntities.findByHandle(handle);
    }

    /** Name and display name shown by the authenticator: the user's email and display name. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveUser(UUID userId) {
        return userService.findActive(userId);
    }

    /** Stores the user handle Spring Security generated for a user (idempotent). */
    @Transactional
    @NoHistory(reason = "technical WebAuthn user handle; PASSKEY_REGISTERED is recorded with the credential")
    public PasskeyUserEntity saveUserEntity(UUID userId, String handle) {
        return userEntities.findByUserId(userId).orElseGet(() -> {
            PasskeyUserEntity entity = new PasskeyUserEntity();
            entity.setUserId(userId);
            entity.setHandle(handle);
            return userEntities.save(entity);
        });
    }

    @Transactional(readOnly = true)
    public Optional<PasskeyCredential> findCredential(String credentialId) {
        return credentials.findByCredentialId(credentialId);
    }

    @Transactional(readOnly = true)
    public List<PasskeyCredential> findCredentialsByHandle(String handle) {
        return userEntities.findByHandle(handle)
                .map(entity -> credentials.findByUserIdOrderByCreatedAt(entity.getUserId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<PasskeyCredential> listForUser(UUID userId) {
        return credentials.findByUserIdOrderByCreatedAt(userId);
    }

    /**
     * Creates (registration) or updates (after an authentication) a credential.
     *
     * @throws ApiException 401 when a new credential is registered without a session, 403 ACCESS_DENIED when the
     *                      user handle belongs to another user
     */
    @Transactional
    public PasskeyCredential saveCredential(PasskeyCredentialData data) {
        Optional<PasskeyCredential> existing = credentials.findByCredentialId(data.credentialId());
        if (existing.isPresent()) {
            PasskeyCredential credential = existing.get();
            credential.setSignatureCount(data.signatureCount());
            credential.setUvInitialized(data.uvInitialized());
            credential.setBackupState(data.backupState());
            credential.setLastUsedAt(clock.instant());
            return credential;
        }
        UUID userId = currentActorService.requireUserId();
        PasskeyUserEntity owner = userEntities.findByHandle(data.userHandle())
                .orElseThrow(() -> ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        if (!owner.getUserId().equals(userId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUserId(userId);
        credential.setCredentialId(data.credentialId());
        credential.setPublicKeyCose(data.publicKeyCose());
        credential.setSignatureCount(data.signatureCount());
        credential.setTransports(StringUtils.abbreviate(data.transports(), 100));
        credential.setLabel(StringUtils.abbreviate(StringUtils.defaultIfBlank(StringUtils.strip(data.label()),
                DEFAULT_LABEL), 100));
        credential.setCredentialType(StringUtils.defaultIfBlank(data.credentialType(), "public-key"));
        credential.setUvInitialized(data.uvInitialized());
        credential.setBackupEligible(data.backupEligible());
        credential.setBackupState(data.backupState());
        credential.setAttestationObject(data.attestationObject());
        credential.setAttestationClientDataJson(data.attestationClientDataJson());
        PasskeyCredential saved = credentials.save(credential);
        history.record(history.event(AuthAction.PASSKEY_REGISTERED, userId)
                .metadata(Map.of("passkeyId", saved.getId().toString(), "label", saved.getLabel()))
                .build());
        return saved;
    }
}
