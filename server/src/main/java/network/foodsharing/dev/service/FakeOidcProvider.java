package network.foodsharing.dev.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * An OpenID Connect provider that stands in for Google in the local and e2e profiles (decision #2): authorization
 * code flow with RS256 id tokens, JWKS and userinfo. The RSA key is generated at startup; codes (2 minutes) and access
 * tokens (1 hour) live in memory. Token timestamps use the real time because Spring Security validates
 * {@code iat}/{@code exp} against the system clock, while the application clock may be fixed (e2e).
 */
@Service
@Profile({"local", "e2e"})
public class FakeOidcProvider {

    static final Duration CODE_TTL = Duration.ofMinutes(2);
    static final Duration TOKEN_TTL = Duration.ofHours(1);
    static final String KEY_ID = "fake-oidc-key";

    private final DevUserCatalog catalog;
    private final UserService userService;
    private final MutableClock clock;
    private final AppProperties properties;
    private final String clientId;
    private final String clientSecret;
    private final RSAKey signingKey;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();
    private final Map<String, IssuedToken> accessTokens = new ConcurrentHashMap<>();

    public FakeOidcProvider(DevUserCatalog catalog, UserService userService, MutableClock clock,
                            AppProperties properties,
                            @Value("${spring.security.oauth2.client.registration.google.client-id:dev-client}") String clientId,
                            @Value("${spring.security.oauth2.client.registration.google.client-secret:dev-secret}") String clientSecret) {
        this.catalog = catalog;
        this.userService = userService;
        this.clock = clock;
        this.properties = properties;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        try {
            this.signingKey = new RSAKeyGenerator(2048).keyID(KEY_ID).keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not generate the fake OIDC signing key", e);
        }
    }

    /** A Google account offered on the authorize page or entered in its "new Google user" form. */
    public record Account(String key, String subject, String email, boolean emailVerified, String name,
                          String givenName, String familyName, String picture, String locale) {

        Map<String, Object> claims() {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", subject);
            claims.put("email", email);
            claims.put("email_verified", emailVerified);
            claims.put("name", name);
            putIfPresent(claims, "given_name", givenName);
            putIfPresent(claims, "family_name", familyName);
            putIfPresent(claims, "picture", picture);
            putIfPresent(claims, "locale", locale);
            return claims;
        }
    }

    /** An OAuth error response ({@code {"error": ..., "error_description": ...}}). */
    public static final class OidcError extends RuntimeException {

        private final HttpStatus status;
        private final String error;

        public OidcError(HttpStatus status, String error, String description) {
            super(description);
            this.status = status;
            this.error = error;
        }

        public HttpStatus status() {
            return status;
        }

        public String error() {
            return error;
        }

        public Map<String, Object> body() {
            return Map.of("error", error, "error_description", getMessage());
        }
    }

    private record PendingCode(String clientId, String redirectUri, String nonce, String scope, Account account,
                               Instant expiresAt) {
    }

    private record IssuedToken(Map<String, Object> claims, Instant expiresAt) {
    }

    public Map<String, Object> discovery(String issuer) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/authorize");
        metadata.put("token_endpoint", issuer + "/token");
        metadata.put("userinfo_endpoint", issuer + "/userinfo");
        metadata.put("jwks_uri", issuer + "/jwks");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("subject_types_supported", List.of("public"));
        metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
        metadata.put("scopes_supported", List.of("openid", "email", "profile"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        metadata.put("grant_types_supported", List.of("authorization_code"));
        metadata.put("claims_supported", List.of("sub", "iss", "aud", "exp", "iat", "nonce", "email",
                "email_verified", "name", "given_name", "family_name", "picture", "locale"));
        return metadata;
    }

    public Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    /** The seed users with a Google identity (foodsaver, bot) as they are in the database now. */
    public List<Account> seedAccounts() {
        return catalog.entries().stream()
                .filter(entry -> entry.googleSubject() != null)
                .flatMap(entry -> userService.findActive(entry.id())
                        .map(user -> seedAccount(entry, user)).stream())
                .toList();
    }

    public Optional<Account> seedAccount(String subject) {
        return seedAccounts().stream().filter(account -> account.subject().equals(subject)).findFirst();
    }

    /**
     * A Google account that has no seed user: the subject is derived from the email, so the same email always maps
     * to the same Google subject.
     */
    public Account newAccount(String email, String name, String locale, boolean emailVerified) {
        String normalized = Objects.requireNonNull(email).strip().toLowerCase(Locale.ROOT);
        String displayName = name == null || name.isBlank() ? normalized.substring(0, normalized.indexOf('@')) : name.strip();
        int space = displayName.indexOf(' ');
        String given = space > 0 ? displayName.substring(0, space) : displayName;
        String family = space > 0 ? displayName.substring(space + 1) : null;
        return new Account("new", DevUserCatalog.GOOGLE_SUBJECT_PREFIX + "new-" + sha256(normalized).substring(0, 16),
                normalized, emailVerified, displayName, given, family, null,
                locale == null || locale.isBlank() ? "de" : locale);
    }

    /** Stores an authorization code for the chosen account (single use, {@link #CODE_TTL}). */
    public String issueCode(String requestClientId, String redirectUri, String nonce, String scope, Account account) {
        purgeExpired();
        String code = randomToken();
        codes.put(code, new PendingCode(requestClientId, redirectUri, nonce, scope, account,
                clock.realInstant().plus(CODE_TTL)));
        return code;
    }

    /**
     * Token endpoint: exchanges a code for an access token and a signed id token.
     *
     * @throws OidcError invalid_client, invalid_grant or unsupported_grant_type
     */
    public Map<String, Object> exchange(String grantType, String code, String redirectUri, String requestClientId,
                                        String requestClientSecret, String issuer) {
        if (!clientId.equals(requestClientId) || !clientSecret.equals(requestClientSecret)) {
            throw new OidcError(HttpStatus.UNAUTHORIZED, "invalid_client", "Unknown client or wrong secret");
        }
        if (!"authorization_code".equals(grantType)) {
            throw new OidcError(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "Only authorization_code is supported");
        }
        PendingCode pending = code == null ? null : codes.remove(code);
        Instant now = clock.realInstant();
        if (pending == null || pending.expiresAt().isBefore(now)) {
            throw new OidcError(HttpStatus.BAD_REQUEST, "invalid_grant", "Unknown, used or expired code");
        }
        if (!pending.clientId().equals(requestClientId) || !Objects.equals(pending.redirectUri(), redirectUri)) {
            throw new OidcError(HttpStatus.BAD_REQUEST, "invalid_grant", "Code was issued for another client or redirect_uri");
        }
        Map<String, Object> claims = pending.account().claims();
        String accessToken = randomToken();
        accessTokens.put(accessToken, new IssuedToken(claims, now.plus(TOKEN_TTL)));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", TOKEN_TTL.toSeconds());
        response.put("scope", pending.scope() == null ? "openid email profile" : pending.scope());
        response.put("id_token", idToken(issuer, requestClientId, pending.nonce(), claims, now));
        return response;
    }

    /**
     * Userinfo endpoint.
     *
     * @throws OidcError 401 invalid_token
     */
    public Map<String, Object> userInfo(String accessToken) {
        IssuedToken token = accessToken == null ? null : accessTokens.get(accessToken);
        if (token == null || token.expiresAt().isBefore(clock.realInstant())) {
            throw new OidcError(HttpStatus.UNAUTHORIZED, "invalid_token", "Unknown or expired access token");
        }
        return token.claims();
    }

    private String idToken(String issuer, String audience, String nonce, Map<String, Object> claims, Instant now) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .claim("azp", audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(TOKEN_TTL)))
                .claim("auth_time", now.getEpochSecond());
        if (nonce != null && !nonce.isBlank()) {
            builder.claim("nonce", nonce);
        }
        claims.forEach(builder::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID)
                .type(JOSEObjectType.JWT).build(), builder.build());
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the id token", e);
        }
        return jwt.serialize();
    }

    private Account seedAccount(DevUserCatalog.Entry entry, User user) {
        String picture = user.getAvatarUrl() == null ? null
                : user.getAvatarUrl().startsWith("/") ? properties.getBaseUrl() + user.getAvatarUrl() : user.getAvatarUrl();
        return new Account(entry.name(), entry.googleSubject(), user.getEmail(), true, user.getDisplayName(),
                user.getFirstName(), user.getLastName(), picture, googleLocale(user.getPreferredLanguage()));
    }

    /** Google reports a BCP 47 locale; Swiss German is reported as de-CH. */
    static String googleLocale(Language language) {
        if (language == null) {
            return "de";
        }
        return language == Language.GWS ? "de-CH" : language.getValue();
    }

    private void purgeExpired() {
        Instant now = clock.realInstant();
        codes.values().removeIf(code -> code.expiresAt().isBefore(now));
        accessTokens.values().removeIf(token -> token.expiresAt().isBefore(now));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
