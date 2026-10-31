package network.foodsharing.auth.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.PasskeyCredential;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.auth.repository.PasskeyCredentialRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current user's login methods (identities + passkeys): list, unlink, rename/delete passkeys. The last login
 * method can never be removed (409 LAST_LOGIN_METHOD_CANNOT_BE_REMOVED).
 */
@Service
public class AuthMethodsService {

    private final AuthIdentityRepository identities;
    private final PasskeyCredentialRepository passkeys;
    private final CurrentActorService currentActorService;
    private final HistoryService history;

    public AuthMethodsService(AuthIdentityRepository identities, PasskeyCredentialRepository passkeys,
                              CurrentActorService currentActorService, HistoryService history) {
        this.identities = identities;
        this.passkeys = passkeys;
        this.currentActorService = currentActorService;
        this.history = history;
    }

    public record LoginMethods(List<AuthIdentity> identities, List<PasskeyCredential> passkeys) {
    }

    @Transactional(readOnly = true)
    public LoginMethods list() {
        UUID userId = currentActorService.requireUserId();
        return new LoginMethods(identities.findByUserId(userId), passkeys.findByUserIdOrderByCreatedAtAscIdAsc(userId));
    }

    /**
     * Unlinks an identity of the current user; records METHOD_REMOVED {provider, identityId}.
     *
     * @throws ApiException 404 RESOURCE_NOT_FOUND, 409 LAST_LOGIN_METHOD_CANNOT_BE_REMOVED
     */
    @Transactional
    public void unlinkIdentity(UUID identityId) {
        UUID userId = currentActorService.requireUserId();
        AuthIdentity identity = identities.findByIdAndUserId(identityId, userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
        requireAnotherMethod(userId);
        identities.delete(identity);
        history.record(history.event(AuthAction.METHOD_REMOVED, userId)
                .metadata(Map.of("provider", identity.getProvider().name(), "identityId", identityId.toString()))
                .build());
    }

    /**
     * Renames a passkey of the current user; records PASSKEY_RENAMED (field label, old → new).
     *
     * @throws ApiException 404 PASSKEY_NOT_FOUND
     */
    @Transactional
    public PasskeyCredential renamePasskey(UUID passkeyId, String label) {
        UUID userId = currentActorService.requireUserId();
        PasskeyCredential passkey = requirePasskey(passkeyId, userId);
        String newLabel = StringUtils.abbreviate(label.strip(), 100);
        if (!newLabel.equals(passkey.getLabel())) {
            history.record(history.event(AuthAction.PASSKEY_RENAMED, userId)
                    .secondarySubject(passkeyId)
                    .change("label", passkey.getLabel(), newLabel)
                    .build());
            passkey.setLabel(newLabel);
        }
        return passkey;
    }

    /**
     * Deletes a passkey of the current user; records PASSKEY_DELETED {passkeyId, label}.
     *
     * @throws ApiException 404 PASSKEY_NOT_FOUND, 409 LAST_LOGIN_METHOD_CANNOT_BE_REMOVED
     */
    @Transactional
    public void deletePasskey(UUID passkeyId) {
        UUID userId = currentActorService.requireUserId();
        PasskeyCredential passkey = requirePasskey(passkeyId, userId);
        requireAnotherMethod(userId);
        passkeys.delete(passkey);
        history.record(history.event(AuthAction.PASSKEY_DELETED, userId)
                .metadata(Map.of("passkeyId", passkeyId.toString(), "label", passkey.getLabel()))
                .build());
    }

    private PasskeyCredential requirePasskey(UUID passkeyId, UUID userId) {
        return passkeys.findByIdAndUserId(passkeyId, userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.PASSKEY_NOT_FOUND));
    }

    private void requireAnotherMethod(UUID userId) {
        if (identities.countByUserId(userId) + passkeys.countByUserId(userId) <= 1) {
            throw ApiException.conflict(ApiErrorCode.LAST_LOGIN_METHOD_CANNOT_BE_REMOVED);
        }
    }
}
