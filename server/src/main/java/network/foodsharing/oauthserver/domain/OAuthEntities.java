package network.foodsharing.oauthserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Entities of the OAuth2/OIDC provider (A44). List columns are space separated. */
public final class OAuthEntities {

    private OAuthEntities() {
    }

    public static Set<String> split(String value) {
        return value == null || value.isBlank() ? new LinkedHashSet<>()
                : Arrays.stream(value.trim().split("\\s+")).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String join(java.util.Collection<?> values) {
        return values == null ? "" : values.stream().map(String::valueOf).collect(Collectors.joining(" "));
    }

    /** A registered partner app. */
    @Getter
    @Setter
    @Entity(name = "OAuthClientRegistration")
    @Table(name = "oauth_client")
    public static class Client extends BaseEntity {
        @Column(name = "client_id", nullable = false, length = 60, unique = true)
        private String clientId;

        @Column(name = "name", nullable = false, length = 200)
        private String name;

        @Column(name = "secret_hash", length = 64)
        private String secretHash;

        @Column(name = "confidential", nullable = false)
        private boolean confidential;

        @Column(name = "redirect_uris", nullable = false, length = 4000)
        private String redirectUris;

        @Column(name = "scopes", nullable = false, length = 200)
        private String scopes;

        @Column(name = "grant_types", nullable = false, length = 100)
        private String grantTypes;

        @Column(name = "required_region_ids", length = 4000)
        private String requiredRegionIds;

        @Column(name = "enabled", nullable = false)
        private boolean enabled = true;

        @Column(name = "deleted_at")
        private Instant deletedAt;

        public List<String> redirectUriList() {
            return List.copyOf(split(redirectUris));
        }

        public Set<String> scopeSet() {
            return split(scopes);
        }

        public Set<String> grantTypeSet() {
            return split(grantTypes);
        }

        public Set<UUID> requiredRegions() {
            return split(requiredRegionIds).stream().map(UUID::fromString)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /** A pending authorization (consent page, then code). */
    @Getter
    @Setter
    @Entity(name = "OAuthAuthorizationRequest")
    @Table(name = "oauth_authorization_request")
    public static class AuthorizationRequest extends BaseEntity {
        @Column(name = "client_id", nullable = false)
        private UUID clientId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "scopes", nullable = false, length = 200)
        private String scopes;

        @Column(name = "redirect_uri", nullable = false, length = 1000)
        private String redirectUri;

        @Column(name = "state", length = 500)
        private String state;

        @Column(name = "nonce", length = 200)
        private String nonce;

        @Column(name = "code_challenge", length = 128)
        private String codeChallenge;

        @Column(name = "code_challenge_method", length = 10)
        private String codeChallengeMethod;

        @Column(name = "expires_at", nullable = false)
        private Instant expiresAt;
    }

    /** A single-use authorization code (hash only). */
    @Getter
    @Setter
    @Entity(name = "OAuthCode")
    @Table(name = "oauth_code")
    public static class Code extends BaseEntity {
        @Column(name = "request_id", nullable = false)
        private UUID requestId;

        @Column(name = "code_hash", nullable = false, length = 64, unique = true)
        private String codeHash;

        @Column(name = "expires_at", nullable = false)
        private Instant expiresAt;

        @Column(name = "used_at")
        private Instant usedAt;
    }

    /** An opaque refresh token (hash only), rotated on use. */
    @Getter
    @Setter
    @Entity(name = "OAuthRefreshToken")
    @Table(name = "oauth_refresh_token")
    public static class RefreshToken extends BaseEntity {
        @Column(name = "client_id", nullable = false)
        private UUID clientId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "scopes", nullable = false, length = 200)
        private String scopes;

        @Column(name = "token_hash", nullable = false, length = 64, unique = true)
        private String tokenHash;

        @Column(name = "expires_at", nullable = false)
        private Instant expiresAt;

        @Column(name = "revoked_at")
        private Instant revokedAt;
    }

    /** A remembered consent ("remember my choice"). */
    @Getter
    @Setter
    @Entity(name = "OAuthConsentGrant")
    @Table(name = "oauth_consent")
    public static class Consent extends BaseEntity {
        @Column(name = "client_id", nullable = false)
        private UUID clientId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "scopes", nullable = false, length = 200)
        private String scopes;
    }
}
