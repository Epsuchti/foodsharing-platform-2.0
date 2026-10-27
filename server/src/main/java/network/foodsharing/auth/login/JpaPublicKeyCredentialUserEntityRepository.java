package network.foodsharing.auth.login;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.PasskeyUserEntity;
import network.foodsharing.auth.service.PasskeyService;
import network.foodsharing.user.domain.User;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Component;

/**
 * WebAuthn user entities backed by {@code passkey_user_entity}. Spring Security's "username" is the platform user id
 * ({@code SessionAuthenticationToken.getName()}); the entity id is the random user handle. The name/display name
 * shown by authenticators are replaced with email and display name when creation options are built
 * ({@code AuthLoginConfig}).
 */
@Component
public class JpaPublicKeyCredentialUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private final PasskeyService passkeys;

    public JpaPublicKeyCredentialUserEntityRepository(PasskeyService passkeys) {
        this.passkeys = passkeys;
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return passkeys.findUserEntityByHandle(id.toBase64UrlString()).map(this::toEntity).orElse(null);
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        return parseUserId(username)
                .flatMap(passkeys::findUserEntityByUserId)
                .map(this::toEntity)
                .orElse(null);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity userEntity) {
        UUID userId = parseUserId(userEntity.getName())
                .orElseThrow(() -> new IllegalArgumentException("WebAuthn user name must be a user id"));
        passkeys.saveUserEntity(userId, userEntity.getId().toBase64UrlString());
    }

    @Override
    public void delete(Bytes id) {
        throw new UnsupportedOperationException("WebAuthn user handles are removed with the account");
    }

    private PublicKeyCredentialUserEntity toEntity(PasskeyUserEntity entity) {
        String displayName = passkeys.findActiveUser(entity.getUserId()).map(User::getDisplayName)
                .orElse(entity.getUserId().toString());
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.fromBase64(entity.getHandle()))
                .name(entity.getUserId().toString())
                .displayName(displayName)
                .build();
    }

    static Optional<UUID> parseUserId(String username) {
        try {
            return Optional.of(UUID.fromString(username));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}
