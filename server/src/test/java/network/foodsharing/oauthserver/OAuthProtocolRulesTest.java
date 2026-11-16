package network.foodsharing.oauthserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.oauthserver.domain.OAuthEntities.AuthorizationRequest;
import network.foodsharing.oauthserver.domain.OAuthEntities.Client;
import network.foodsharing.oauthserver.domain.OAuthEntities.Code;
import network.foodsharing.oauthserver.domain.OAuthEntities.Consent;
import network.foodsharing.oauthserver.domain.OAuthEntities.RefreshToken;
import network.foodsharing.oauthserver.repository.OAuthClientRepository;
import network.foodsharing.oauthserver.repository.OAuthCodeRepository;
import network.foodsharing.oauthserver.repository.OAuthConsentRepository;
import network.foodsharing.oauthserver.repository.OAuthRefreshTokenRepository;
import network.foodsharing.oauthserver.repository.OAuthRequestRepository;
import network.foodsharing.oauthserver.service.AuthorizationService;
import network.foodsharing.oauthserver.service.OAuthCrypto;
import network.foodsharing.oauthserver.service.OAuthGrantService;
import network.foodsharing.oauthserver.service.OAuthKeys;
import network.foodsharing.oauthserver.service.OAuthProtocolException;
import network.foodsharing.oauthserver.service.TokenService;
import network.foodsharing.oauthserver.service.UserInfoService;
import network.foodsharing.oauthserver.web.OAuthEndpointsController;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * C13 (A44) protocol rules that the e2e OAuth spec asserted through the API: a code is single use (a replay revokes the
 * refresh tokens), revoked refresh tokens and old client secrets are refused, a region-restricted client refuses an
 * approval from outside its regions, revoking a connection revokes the tokens, and the discovery document and JWKS
 * describe the provider without the private key. Plain Mockito, no Spring context.
 */
class OAuthProtocolRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final UUID USER = UUID.fromString("00000001-0001-4000-8000-000000000003");
    private static final UUID REGION = UUID.fromString("00000010-0001-4000-8000-000000000040");
    private static final String SECRET = "wiki-local-secret";

    private final OAuthClientRepository clients = mock(OAuthClientRepository.class);
    private final OAuthRequestRepository requests = mock(OAuthRequestRepository.class);
    private final OAuthCodeRepository codes = mock(OAuthCodeRepository.class);
    private final OAuthConsentRepository consents = mock(OAuthConsentRepository.class);
    private final OAuthRefreshTokenRepository refreshTokens = mock(OAuthRefreshTokenRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final ConfigurationService configuration = mock(ConfigurationService.class);
    private final AppProperties properties = new AppProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AuthorizationService authorization;
    private TokenService tokens;
    private Client wiki;

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        properties.setBaseUrl("https://foodsharing.example/");
        authorization = new AuthorizationService(clients, requests, codes, consents, regions,
                mock(FeatureToggleService.class), currentActor, history, properties, clock);
        tokens = new TokenService(clients, requests, codes, refreshTokens, mock(OAuthKeys.class), authorization,
                mock(UserService.class), history, properties, clock);

        wiki = new Client();
        ReflectionTestUtils.setField(wiki, "id", UUID.randomUUID());
        wiki.setClientId("wiki");
        wiki.setName("Foodsharing Wiki Zürich");
        wiki.setConfidential(true);
        wiki.setSecretHash(OAuthCrypto.sha256Hex(SECRET));
        wiki.setRedirectUris("https://wiki.example.org/callback");
        wiki.setScopes("openid profile");
        wiki.setGrantTypes("authorization_code refresh_token");
        wiki.setRequiredRegionIds(REGION.toString());
        when(clients.findByClientIdAndDeletedAtIsNull("wiki")).thenReturn(Optional.of(wiki));
        when(clients.findById(wiki.getId())).thenReturn(Optional.of(wiki));
    }

    private AuthorizationRequest openRequest() {
        AuthorizationRequest request = new AuthorizationRequest();
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        request.setClientId(wiki.getId());
        request.setUserId(USER);
        request.setScopes("openid profile");
        request.setRedirectUri("https://wiki.example.org/callback");
        request.setExpiresAt(NOW.plusSeconds(300));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        return request;
    }

    private RefreshToken liveRefreshToken() {
        RefreshToken token = new RefreshToken();
        token.setClientId(wiki.getId());
        token.setUserId(USER);
        token.setScopes("openid profile");
        token.setExpiresAt(NOW.plusSeconds(86_400));
        return token;
    }

    private static Map<String, String> codeGrant(String code, String secret) {
        return Map.of("grant_type", "authorization_code", "code", code, "client_id", "wiki", "client_secret", secret,
                "redirect_uri", "https://wiki.example.org/callback");
    }

    @Test
    void aCodeIsSingleUseAndAReplayRevokesTheRefreshTokens() {
        AuthorizationRequest request = openRequest();
        Code used = new Code();
        used.setRequestId(request.getId());
        used.setCodeHash(OAuthCrypto.sha256Hex("the-code"));
        used.setExpiresAt(NOW.plusSeconds(60));
        used.setUsedAt(NOW.minusSeconds(5));
        when(codes.findByCodeHash(used.getCodeHash())).thenReturn(Optional.of(used));
        RefreshToken issued = liveRefreshToken();
        when(refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(USER, wiki.getId())).thenReturn(List.of(issued));

        assertThatThrownBy(() -> tokens.token(codeGrant("the-code", SECRET), null))
                .isInstanceOfSatisfying(OAuthProtocolException.class, e -> {
                    assertThat(e.status()).isEqualTo(400);
                    assertThat(e.error()).isEqualTo("invalid_grant");
                });
        assertThat(issued.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void anOldSecretFailsClientAuthenticationBeforeTheCodeIsLookedAt() {
        // after a rotation the stored hash belongs to the new secret
        wiki.setSecretHash(OAuthCrypto.sha256Hex("the-new-secret-of-48-characters-xxxxxxxxxxxxxxxx"));

        assertThatThrownBy(() -> tokens.token(codeGrant("nope", SECRET), null))
                .isInstanceOfSatisfying(OAuthProtocolException.class, e -> {
                    assertThat(e.status()).isEqualTo(401);
                    assertThat(e.error()).isEqualTo("invalid_client");
                });
        verify(codes, never()).findByCodeHash(any());

        // the new secret passes client authentication; the made-up code is the grant's problem
        assertThatThrownBy(() -> tokens.token(
                codeGrant("nope", "the-new-secret-of-48-characters-xxxxxxxxxxxxxxxx"), null))
                .isInstanceOfSatisfying(OAuthProtocolException.class, e -> assertThat(e.error()).isEqualTo("invalid_grant"));
    }

    @Test
    void aRevokedRefreshTokenIsRefused() {
        RefreshToken revoked = liveRefreshToken();
        revoked.setTokenHash(OAuthCrypto.sha256Hex("the-refresh-token"));
        revoked.setRevokedAt(NOW.minusSeconds(60));
        when(refreshTokens.findByTokenHash(revoked.getTokenHash())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> tokens.token(Map.of("grant_type", "refresh_token", "refresh_token", "the-refresh-token",
                "client_id", "wiki", "client_secret", SECRET), null))
                .isInstanceOfSatisfying(OAuthProtocolException.class, e -> assertThat(e.error()).isEqualTo("invalid_grant"));
    }

    @Test
    void aRegionRestrictedClientRefusesAnApprovalFromOutsideItsRegions() {
        AuthorizationRequest request = openRequest();
        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(USER, UUID.randomUUID(), Role.FOODSAVER,
                "newbie@local"));
        when(codes.findByRequestIdIn(List.of(request.getId()))).thenReturn(List.of());
        when(regions.activeRegionIdsOf(USER)).thenReturn(Set.of(UUID.randomUUID()));

        assertThat(authorization.regionAllowed(wiki, USER)).isFalse();
        assertThatThrownBy(() -> authorization.decide(request.getId(), true, true))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.OAUTH_REGION_REQUIRED));
        verify(consents, never()).save(any());

        // a member of the region may; a client without regions is open to everybody
        when(regions.activeRegionIdsOf(USER)).thenReturn(Set.of(REGION));
        assertThat(authorization.regionAllowed(wiki, USER)).isTrue();
        wiki.setRequiredRegionIds(null);
        when(regions.activeRegionIdsOf(USER)).thenReturn(Set.of());
        assertThat(authorization.regionAllowed(wiki, USER)).isTrue();
    }

    @Test
    void revokingAConnectionDeletesTheConsentAndRevokesTheTokens() {
        OAuthGrantService grants = new OAuthGrantService(clients, consents, refreshTokens, requests, codes, history, clock);
        Consent consent = new Consent();
        consent.setClientId(wiki.getId());
        consent.setUserId(USER);
        consent.setScopes("openid profile");
        when(consents.findByUserId(USER)).thenReturn(List.of(consent));
        RefreshToken issued = liveRefreshToken();
        when(refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(USER, wiki.getId())).thenReturn(List.of(issued));

        grants.revoke(USER, "wiki");

        verify(consents).delete(consent);
        assertThat(issued.getRevokedAt()).isEqualTo(NOW);
        verify(history).record(any());

        // nothing left to revoke: the connection is unknown
        when(consents.findByUserId(USER)).thenReturn(List.of());
        when(refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(USER, wiki.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> grants.revoke(USER, "wiki"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.OAUTH_CLIENT_NOT_FOUND));
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoveryAndJwksDescribeTheProviderWithoutThePrivateKey() {
        OAuthKeys keys = new OAuthKeys(properties, configuration);
        OAuthEndpointsController endpoints = new OAuthEndpointsController(authorization, tokens,
                mock(UserInfoService.class), keys, currentActor);

        Map<String, Object> doc = endpoints.discovery();
        String issuer = "https://foodsharing.example";
        assertThat(doc).containsEntry("issuer", issuer)
                .containsEntry("authorization_endpoint", issuer + "/oauth/authorize")
                .containsEntry("token_endpoint", issuer + "/oauth/token")
                .containsEntry("userinfo_endpoint", issuer + "/oauth/userinfo")
                .containsEntry("jwks_uri", issuer + "/.well-known/jwks.json")
                .containsEntry("code_challenge_methods_supported", List.of("S256"))
                .containsEntry("id_token_signing_alg_values_supported", List.of("RS256"));
        assertThat((List<String>) doc.get("scopes_supported")).contains("openid", "profile", "email", "regions");

        List<Map<String, Object>> jwks = (List<Map<String, Object>>) endpoints.jwks().get("keys");
        assertThat(jwks).hasSize(1);
        assertThat(jwks.getFirst()).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsEntry("use", "sig")
                .containsKey("n").doesNotContainKey("d");
        assertThat((String) jwks.getFirst().get("kid")).matches("[0-9a-f]{16}");
    }
}
