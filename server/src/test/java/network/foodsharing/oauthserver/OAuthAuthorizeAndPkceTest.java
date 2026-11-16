package network.foodsharing.oauthserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import network.foodsharing.oauthserver.service.OAuthClientService;
import network.foodsharing.oauthserver.service.OAuthKeys;
import network.foodsharing.oauthserver.service.OAuthProtocolException;
import network.foodsharing.oauthserver.service.TokenService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * C13 authorization rules the deleted OAuthFlowIT walked over HTTP (decision 180): a public client needs PKCE S256 and
 * only its registered scopes, an unregistered redirect URI is refused outright, the consent request belongs to the
 * user who started it and ends once answered, a decline returns {@code access_denied} with the state, a remembered
 * consent skips the question, the token endpoint checks the PKCE verifier, a refresh token rotates and reusing the
 * old one revokes the whole family, and the client administration refuses duplicate ids and secrets for public
 * clients. The browser walk is journey {@code orga/connect-a-partner-app}; code reuse, old secrets, revoked refresh
 * tokens and region restriction are in OAuthProtocolRulesTest.
 */
class OAuthAuthorizeAndPkceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final UUID USER = UUID.fromString("00000001-0001-4000-8000-000000000007");
    private static final String CALLBACK = "https://app.example.org/cb";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private final OAuthClientRepository clients = mock(OAuthClientRepository.class);
    private final OAuthRequestRepository requests = mock(OAuthRequestRepository.class);
    private final OAuthCodeRepository codes = mock(OAuthCodeRepository.class);
    private final OAuthConsentRepository consents = mock(OAuthConsentRepository.class);
    private final OAuthRefreshTokenRepository refreshTokens = mock(OAuthRefreshTokenRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final UserService users = mock(UserService.class);
    private final AppProperties properties = new AppProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Map<UUID, AuthorizationRequest> savedRequests = new HashMap<>();
    private final List<Code> savedCodes = new ArrayList<>();
    private final List<RefreshToken> savedTokens = new ArrayList<>();
    private AuthorizationService authorization;
    private TokenService tokens;
    private Client app;

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        properties.setBaseUrl("https://foodsharing.example/");
        authorization = new AuthorizationService(clients, requests, codes, consents, regions,
                mock(FeatureToggleService.class), currentActor, history, properties, clock);
        tokens = new TokenService(clients, requests, codes, refreshTokens,
                new OAuthKeys(properties, mock(ConfigurationService.class)), authorization, users, history,
                properties, clock);
        app = new Client();
        ReflectionTestUtils.setField(app, "id", UUID.randomUUID());
        app.setClientId("mobile-dev");
        app.setName("Mobile");
        app.setConfidential(false);
        app.setEnabled(true);
        app.setRedirectUris(CALLBACK);
        app.setScopes("openid profile email");
        app.setGrantTypes("authorization_code refresh_token");
        when(clients.findByClientIdAndDeletedAtIsNull("mobile-dev")).thenReturn(Optional.of(app));
        when(clients.findById(app.getId())).thenReturn(Optional.of(app));
        when(consents.findByClientIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(requests.save(any())).thenAnswer(i -> {
            AuthorizationRequest request = i.getArgument(0);
            if (request.getId() == null) {
                ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
            }
            savedRequests.put(request.getId(), request);
            return request;
        });
        when(requests.findById(any())).thenAnswer(i -> Optional.ofNullable(savedRequests.get(i.getArgument(0))));
        when(codes.save(any())).thenAnswer(i -> {
            savedCodes.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(codes.findByRequestIdIn(any())).thenAnswer(i -> savedCodes.stream()
                .filter(c -> i.<List<UUID>>getArgument(0).contains(c.getRequestId())).toList());
        when(codes.findByCodeHash(any())).thenAnswer(i -> savedCodes.stream()
                .filter(c -> c.getCodeHash().equals(i.getArgument(0))).findFirst());
        when(refreshTokens.save(any())).thenAnswer(i -> {
            savedTokens.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(refreshTokens.findByTokenHash(any())).thenAnswer(i -> savedTokens.stream()
                .filter(t -> t.getTokenHash().equals(i.getArgument(0))).findFirst());
        when(refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(any(), any())).thenAnswer(i -> savedTokens.stream()
                .filter(t -> t.getRevokedAt() == null).toList());
        when(users.findActive(USER)).thenReturn(Optional.of(new User()));
        signedIn(USER);
    }

    private void signedIn(UUID userId) {
        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(userId, UUID.randomUUID(),
                Role.FOODSAVER, "jumper@local"));
    }

    private String authorize(String scope, String challenge, String redirectUri) {
        Map<String, String> params = new HashMap<>(Map.of("response_type", "code", "client_id", "mobile-dev",
                "redirect_uri", redirectUri, "scope", scope, "state", "st-1"));
        if (challenge != null) {
            params.put("code_challenge", challenge);
            params.put("code_challenge_method", "S256");
        }
        return authorization.authorize(params, USER);
    }

    private static String query(String location, String name) {
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst(name);
    }

    private Map<String, Object> exchange(String code, String verifier) {
        return tokens.token(Map.of("grant_type", "authorization_code", "client_id", "mobile-dev", "code", code,
                "redirect_uri", CALLBACK, "code_verifier", verifier), null);
    }

    private static String oauthError(Runnable call) {
        try {
            call.run();
        } catch (OAuthProtocolException e) {
            return e.error();
        }
        return null;
    }

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.getCode();
        }
        return null;
    }

    @Test
    void aPublicClientNeedsPkceOnlyItsScopesAndARegisteredRedirectUri() {
        assertThat(authorize("openid profile", null, CALLBACK)).startsWith(CALLBACK + "?error=invalid_request")
                .contains("state=st-1");
        assertThat(authorize("openid regions", CHALLENGE, CALLBACK)).contains("error=invalid_scope");
        assertThat(oauthError(() -> authorize("openid", CHALLENGE, "https://evil.example.org/cb")))
                .isEqualTo("invalid_request");
        assertThat(authorize("openid profile", CHALLENGE, CALLBACK)).startsWith(AuthorizationService.CONSENT_PAGE);
    }

    @Test
    void theConsentRequestIsTheUsersAndEndsOnceAnswered() {
        UUID requestId = UUID.fromString(query(authorize("openid profile", CHALLENGE, CALLBACK), "request"));
        assertThat(authorization.consentRequest(requestId).client().getClientId()).isEqualTo("mobile-dev");

        signedIn(UUID.randomUUID());
        assertThat(code(() -> authorization.consentRequest(requestId))).isEqualTo(ApiErrorCode.ACCESS_DENIED);

        signedIn(USER);
        String redirect = authorization.decide(requestId, true, false);
        assertThat(redirect).startsWith(CALLBACK + "?code=").contains("state=st-1");
        assertThat(code(() -> authorization.consentRequest(requestId))).isEqualTo(ApiErrorCode.OAUTH_REQUEST_EXPIRED);
    }

    @Test
    void aDeclineGoesBackWithAccessDeniedAndTheState() {
        UUID requestId = UUID.fromString(query(authorize("openid profile", CHALLENGE, CALLBACK), "request"));
        assertThat(authorization.decide(requestId, false, false))
                .isEqualTo(CALLBACK + "?error=access_denied&state=st-1");
        assertThat(code(() -> authorization.decide(requestId, true, false)))
                .isEqualTo(ApiErrorCode.OAUTH_REQUEST_EXPIRED);
    }

    @Test
    void aRememberedConsentSkipsTheQuestion() {
        Consent remembered = new Consent();
        remembered.setClientId(app.getId());
        remembered.setUserId(USER);
        remembered.setScopes("openid profile email");
        when(consents.findByClientIdAndUserId(app.getId(), USER)).thenReturn(Optional.of(remembered));
        assertThat(authorize("openid profile", CHALLENGE, CALLBACK)).startsWith(CALLBACK + "?code=");
    }

    @Test
    void theTokenEndpointChecksTheVerifierAndRotatesRefreshTokens() {
        UUID requestId = UUID.fromString(query(authorize("openid profile", CHALLENGE, CALLBACK), "request"));
        String code = query(authorization.decide(requestId, true, false), "code");

        assertThat(oauthError(() -> exchange(code, VERIFIER.replace('d', 'e')))).isEqualTo("invalid_grant");
        Map<String, Object> issued = exchange(code, VERIFIER);
        assertThat(issued).containsKeys("access_token", "id_token", "refresh_token")
                .containsEntry("token_type", "Bearer");

        String first = (String) issued.get("refresh_token");
        Map<String, Object> rotated = tokens.token(Map.of("grant_type", "refresh_token", "client_id", "mobile-dev",
                "refresh_token", first), null);
        String second = (String) rotated.get("refresh_token");
        assertThat(second).isNotEqualTo(first);
        // reusing the old token revokes the family: the rotated one is dead too
        assertThat(oauthError(() -> tokens.token(Map.of("grant_type", "refresh_token", "client_id", "mobile-dev",
                "refresh_token", first), null))).isEqualTo("invalid_grant");
        assertThat(oauthError(() -> tokens.token(Map.of("grant_type", "refresh_token", "client_id", "mobile-dev",
                "refresh_token", second), null))).isEqualTo("invalid_grant");
    }

    @Test
    void clientAdministrationRefusesDuplicateIdsAndSecretsForPublicClients() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.isOrga(any())).thenReturn(true);
        OAuthClientService admin = new OAuthClientService(clients, consents, refreshTokens, regions, currentActor,
                permissions, history, clock);
        when(clients.existsByClientId("mobile-dev")).thenReturn(true);
        assertThat(code(() -> admin.create(new OAuthClientService.ClientInput("mobile-dev", "Again", true,
                List.of(CALLBACK), List.of("openid"), List.of("authorization_code"), List.of(), true))))
                .isEqualTo(ApiErrorCode.OAUTH_CLIENT_EXISTS);
        assertThat(code(() -> admin.rotateSecret("mobile-dev"))).isEqualTo(ApiErrorCode.OAUTH_CLIENT_PUBLIC);
        assertThat(app.getSecretHash()).isNull();

        when(permissions.isOrga(any())).thenReturn(false);
        assertThat(code(admin::list)).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }
}
