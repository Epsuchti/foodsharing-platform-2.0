package network.foodsharing.oauthserver.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.oauthserver.domain.OAuthAction;
import network.foodsharing.oauthserver.domain.OAuthEntities;
import network.foodsharing.oauthserver.domain.OAuthEntities.AuthorizationRequest;
import network.foodsharing.oauthserver.domain.OAuthEntities.Client;
import network.foodsharing.oauthserver.domain.OAuthEntities.Code;
import network.foodsharing.oauthserver.domain.OAuthEntities.RefreshToken;
import network.foodsharing.oauthserver.domain.OAuthScope;
import network.foodsharing.oauthserver.repository.OAuthClientRepository;
import network.foodsharing.oauthserver.repository.OAuthCodeRepository;
import network.foodsharing.oauthserver.repository.OAuthRefreshTokenRepository;
import network.foodsharing.oauthserver.repository.OAuthRequestRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.user.service.UserService;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Token endpoint (A44): client authentication ({@code client_secret_basic} / {@code client_secret_post}, public
 * clients by id), {@code authorization_code} (single use, redirect URI and PKCE verifier checked; a replayed code
 * revokes the user's refresh tokens of that client) and {@code refresh_token} (rotation; a reused token revokes the
 * family). Access tokens are RS256 JWTs ({@code token_use=access}), ID tokens are issued for {@code openid}.
 */
@Service
public class TokenService {

    public record AccessClaims(UUID userId, String clientId, Set<String> scopes) {
    }

    private final OAuthClientRepository clients;
    private final OAuthRequestRepository requests;
    private final OAuthCodeRepository codes;
    private final OAuthRefreshTokenRepository refreshTokens;
    private final OAuthKeys keys;
    private final AuthorizationService authorization;
    private final UserService users;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public TokenService(OAuthClientRepository clients, OAuthRequestRepository requests, OAuthCodeRepository codes,
                        OAuthRefreshTokenRepository refreshTokens, OAuthKeys keys, AuthorizationService authorization,
                        UserService users, HistoryService history, AppProperties properties, Clock clock) {
        this.clients = clients;
        this.requests = requests;
        this.codes = codes;
        this.refreshTokens = refreshTokens;
        this.keys = keys;
        this.authorization = authorization;
        this.users = users;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Handles a token request; the answer is the JSON body of a successful response.
     *
     * @throws OAuthProtocolException invalid_client (401), invalid_grant / invalid_request /
     *                                unsupported_grant_type / unauthorized_client (400)
     */
    @Transactional(noRollbackFor = OAuthProtocolException.class)
    @NoHistory(reason = "TOKEN_ISSUED is recorded by issue() for every successful grant")
    public Map<String, Object> token(Map<String, String> params, String authorizationHeader) {
        authorization.requireEnabled();
        Client client = authenticate(params, authorizationHeader);
        String grant = params.get("grant_type");
        if ("authorization_code".equals(grant)) {
            return exchangeCode(client, params);
        }
        if ("refresh_token".equals(grant)) {
            if (!client.grantTypeSet().contains("refresh_token")) {
                throw new OAuthProtocolException(400, "unauthorized_client", "refresh_token grant not allowed");
            }
            return refresh(client, params.get("refresh_token"));
        }
        throw new OAuthProtocolException(400, "unsupported_grant_type", "unsupported grant_type");
    }

    /** Validates a Bearer access token. @throws OAuthProtocolException invalid_token (401) */
    public AccessClaims verifyAccessToken(String token) {
        try {
            JwtConsumer consumer = new JwtConsumerBuilder().setRequireExpirationTime().setRequireSubject()
                    .setEvaluationTime(NumericDate.fromMilliseconds(clock.millis())).setAllowedClockSkewInSeconds(30)
                    .setExpectedIssuer(keys.issuer()).setSkipDefaultAudienceValidation()
                    .setVerificationKey(keys.key().getPublicKey())
                    .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT,
                            AlgorithmIdentifiers.RSA_USING_SHA256).build();
            JwtClaims claims = consumer.processToClaims(token);
            if (!"access".equals(claims.getClaimValueAsString("token_use"))) {
                throw OAuthProtocolException.invalidToken("not an access token");
            }
            return new AccessClaims(UUID.fromString(claims.getSubject()), claims.getClaimValueAsString("client_id"),
                    OAuthEntities.split(claims.getClaimValueAsString("scope")));
        } catch (InvalidJwtException | MalformedClaimException | IllegalArgumentException e) {
            throw OAuthProtocolException.invalidToken("invalid or expired access token");
        }
    }

    private Client authenticate(Map<String, String> params, String header) {
        String clientId = params.get("client_id");
        String secret = params.get("client_secret");
        if (header != null && header.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon < 0) {
                    throw OAuthProtocolException.invalidClient("malformed basic credentials");
                }
                clientId = java.net.URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
                secret = java.net.URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw OAuthProtocolException.invalidClient("malformed basic credentials");
            }
        }
        if (clientId == null) {
            throw OAuthProtocolException.invalidClient("client authentication failed");
        }
        Client client = clients.findByClientIdAndDeletedAtIsNull(clientId).filter(Client::isEnabled)
                .orElseThrow(() -> OAuthProtocolException.invalidClient("client authentication failed"));
        if (client.isConfidential()
                && (secret == null || !OAuthCrypto.sameHash(OAuthCrypto.sha256Hex(secret), client.getSecretHash()))) {
            throw OAuthProtocolException.invalidClient("client authentication failed");
        }
        return client;
    }

    private Map<String, Object> exchangeCode(Client client, Map<String, String> params) {
        String code = params.get("code");
        if (code == null) {
            throw OAuthProtocolException.invalidRequest("code missing");
        }
        Code row = codes.findByCodeHash(OAuthCrypto.sha256Hex(code))
                .orElseThrow(() -> OAuthProtocolException.invalidGrant("unknown code"));
        AuthorizationRequest request = requests.findById(row.getRequestId())
                .orElseThrow(() -> OAuthProtocolException.invalidGrant("unknown code"));
        Instant now = clock.instant();
        if (!request.getClientId().equals(client.getId())) {
            throw OAuthProtocolException.invalidGrant("code was issued to another client");
        }
        if (row.getUsedAt() != null) {
            refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(request.getUserId(), client.getId())
                    .forEach(t -> t.setRevokedAt(now));
            throw OAuthProtocolException.invalidGrant("code already used");
        }
        if (!row.getExpiresAt().isAfter(now)) {
            throw OAuthProtocolException.invalidGrant("code expired");
        }
        if (!request.getRedirectUri().equals(params.get("redirect_uri"))) {
            throw OAuthProtocolException.invalidGrant("redirect_uri mismatch");
        }
        if (request.getCodeChallenge() != null
                && !OAuthCrypto.verifyS256(params.get("code_verifier"), request.getCodeChallenge())) {
            throw OAuthProtocolException.invalidGrant("PKCE verification failed");
        }
        row.setUsedAt(now);
        requireActiveUser(request.getUserId());
        return issue(client, request.getUserId(), OAuthEntities.split(request.getScopes()), request.getNonce(),
                "authorization_code");
    }

    private Map<String, Object> refresh(Client client, String token) {
        if (token == null) {
            throw OAuthProtocolException.invalidRequest("refresh_token missing");
        }
        RefreshToken row = refreshTokens.findByTokenHash(OAuthCrypto.sha256Hex(token))
                .orElseThrow(() -> OAuthProtocolException.invalidGrant("unknown refresh token"));
        Instant now = clock.instant();
        if (!row.getClientId().equals(client.getId())) {
            throw OAuthProtocolException.invalidGrant("refresh token of another client");
        }
        if (row.getRevokedAt() != null) {
            refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(row.getUserId(), client.getId())
                    .forEach(t -> t.setRevokedAt(now));
            throw OAuthProtocolException.invalidGrant("refresh token revoked");
        }
        if (!row.getExpiresAt().isAfter(now)) {
            throw OAuthProtocolException.invalidGrant("refresh token expired");
        }
        row.setRevokedAt(now);
        requireActiveUser(row.getUserId());
        return issue(client, row.getUserId(), OAuthEntities.split(row.getScopes()), null, "refresh_token");
    }

    private void requireActiveUser(UUID userId) {
        if (users.findActive(userId).isEmpty()) {
            throw OAuthProtocolException.invalidGrant("account not active");
        }
    }

    private Map<String, Object> issue(Client client, UUID userId, Set<String> scopes, String nonce, String grant) {
        Instant now = clock.instant();
        long accessSeconds = Duration.ofMinutes(properties.getOauth().getAccessTokenMinutes()).toSeconds();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", sign(claims(client, userId, now, accessSeconds, c -> {
            c.setClaim("token_use", "access");
            c.setClaim("client_id", client.getClientId());
            c.setClaim("scope", OAuthEntities.join(scopes));
        })));
        body.put("token_type", "Bearer");
        body.put("expires_in", accessSeconds);
        body.put("scope", OAuthEntities.join(scopes));
        if (client.grantTypeSet().contains("refresh_token")) {
            String refresh = OAuthCrypto.randomToken(32);
            RefreshToken row = new RefreshToken();
            row.setClientId(client.getId());
            row.setUserId(userId);
            row.setScopes(OAuthEntities.join(scopes));
            row.setTokenHash(OAuthCrypto.sha256Hex(refresh));
            row.setExpiresAt(now.plus(Duration.ofDays(properties.getOauth().getRefreshTokenDays())));
            refreshTokens.save(row);
            body.put("refresh_token", refresh);
        }
        if (scopes.contains(OAuthScope.openid.name())) {
            body.put("id_token", sign(claims(client, userId, now, accessSeconds, c -> {
                if (nonce != null) {
                    c.setClaim("nonce", nonce);
                }
                c.setClaim("azp", client.getClientId());
            })));
        }
        history.record(history.event(OAuthAction.TOKEN_ISSUED, client.getId())
                .metadata(Map.of("grant", grant, "userId", userId.toString(), "scopes", OAuthEntities.join(scopes)))
                .build());
        return body;
    }

    private JwtClaims claims(Client client, UUID userId, Instant now, long seconds,
                             java.util.function.Consumer<JwtClaims> extra) {
        JwtClaims claims = new JwtClaims();
        claims.setIssuer(keys.issuer());
        claims.setSubject(userId.toString());
        claims.setAudience(client.getClientId());
        claims.setIssuedAt(NumericDate.fromMilliseconds(now.toEpochMilli()));
        claims.setExpirationTime(NumericDate.fromMilliseconds(now.plusSeconds(seconds).toEpochMilli()));
        claims.setGeneratedJwtId();
        extra.accept(claims);
        return claims;
    }

    private String sign(JwtClaims claims) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            jws.setPayload(claims.toJson());
            jws.setKey(keys.key().getPrivateKey());
            jws.setKeyIdHeaderValue(keys.key().getKeyId());
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new IllegalStateException("Could not sign token", e);
        }
    }
}
