package network.foodsharing.oauthserver.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.oauthserver.domain.OAuthAction;
import network.foodsharing.oauthserver.domain.OAuthEntities;
import network.foodsharing.oauthserver.domain.OAuthEntities.AuthorizationRequest;
import network.foodsharing.oauthserver.domain.OAuthEntities.Client;
import network.foodsharing.oauthserver.domain.OAuthEntities.Code;
import network.foodsharing.oauthserver.domain.OAuthEntities.Consent;
import network.foodsharing.oauthserver.domain.OAuthScope;
import network.foodsharing.oauthserver.repository.OAuthClientRepository;
import network.foodsharing.oauthserver.repository.OAuthCodeRepository;
import network.foodsharing.oauthserver.repository.OAuthConsentRepository;
import network.foodsharing.oauthserver.repository.OAuthRequestRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization endpoint and consent (A44, RFC 6749 §4.1 + RFC 7636). Errors before the redirect URI is trusted
 * (unknown client, unregistered redirect) are answered directly ({@link OAuthProtocolException}); later errors go back
 * to the client as {@code error=...&state=...}. PKCE S256 is mandatory for public clients, optional for confidential
 * ones ({@code plain} is never accepted). A remembered consent whose scopes cover the request and a passing region
 * check skip the consent page.
 */
@Service
public class AuthorizationService {

    public static final String CONSENT_PAGE = "/oauth/consent?request=";

    public record ConsentView(AuthorizationRequest request, Client client, Set<String> scopes,
                              boolean alreadyRemembered, boolean regionAllowed) {
    }

    private final OAuthClientRepository clients;
    private final OAuthRequestRepository requests;
    private final OAuthCodeRepository codes;
    private final OAuthConsentRepository consents;
    private final RegionService regions;
    private final FeatureToggleService toggles;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public AuthorizationService(OAuthClientRepository clients, OAuthRequestRepository requests,
                                OAuthCodeRepository codes, OAuthConsentRepository consents, RegionService regions,
                                FeatureToggleService toggles, CurrentActorService currentActor, HistoryService history,
                                AppProperties properties, Clock clock) {
        this.clients = clients;
        this.requests = requests;
        this.codes = codes;
        this.consents = consents;
        this.regions = regions;
        this.toggles = toggles;
        this.currentActor = currentActor;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** 404 FEATURE_DISABLED when the provider is switched off. */
    public void requireEnabled() {
        toggles.requireEnabled(FeatureToggle.OAUTH_PROVIDER);
    }

    /**
     * Validates an authorization request of the signed-in user and answers the redirect location: the client's
     * redirect URI with a code (remembered consent) or an error, or the consent page.
     *
     * @throws OAuthProtocolException 400 for an unknown client or unregistered redirect URI
     */
    @Transactional
    @NoHistory(reason = "pending request rows only; consent and token issuance record CONSENT_GRANTED / TOKEN_ISSUED")
    public String authorize(Map<String, String> params, UUID userId) {
        requireEnabled();
        Client client = clients.findByClientIdAndDeletedAtIsNull(params.get("client_id")).filter(Client::isEnabled)
                .orElseThrow(() -> OAuthProtocolException.invalidRequest("unknown or disabled client_id"));
        String redirectUri = params.get("redirect_uri");
        if (redirectUri == null && client.redirectUriList().size() == 1) {
            redirectUri = client.redirectUriList().getFirst();
        }
        if (redirectUri == null || !client.redirectUriList().contains(redirectUri)) {
            throw OAuthProtocolException.invalidRequest("redirect_uri is not registered for this client");
        }
        String state = params.get("state");
        if (state != null && state.length() > 500) {
            throw OAuthProtocolException.invalidRequest("state too long");
        }
        if (!"code".equals(params.get("response_type"))) {
            return error(redirectUri, "unsupported_response_type", state);
        }
        Set<String> scopes = OAuthEntities.split(params.get("scope"));
        if (scopes.isEmpty() || !scopes.contains(OAuthScope.openid.name()) || !client.scopeSet().containsAll(scopes)) {
            return error(redirectUri, "invalid_scope", state);
        }
        String challenge = params.get("code_challenge");
        String method = params.get("code_challenge_method");
        if (challenge == null && !client.isConfidential()) {
            return error(redirectUri, "invalid_request", state);
        }
        if (challenge != null && (!"S256".equals(method) || !challenge.matches("[A-Za-z0-9_-]{43,128}"))) {
            return error(redirectUri, "invalid_request", state);
        }
        String nonce = params.get("nonce");
        if (nonce != null && nonce.length() > 200) {
            return error(redirectUri, "invalid_request", state);
        }
        AuthorizationRequest request = new AuthorizationRequest();
        request.setClientId(client.getId());
        request.setUserId(userId);
        request.setScopes(OAuthEntities.join(scopes));
        request.setRedirectUri(redirectUri);
        request.setState(state);
        request.setNonce(nonce);
        request.setCodeChallenge(challenge);
        request.setCodeChallengeMethod(challenge == null ? null : method);
        request.setExpiresAt(clock.instant().plus(Duration.ofMinutes(properties.getOauth().getCodeMinutes())));
        AuthorizationRequest saved = requests.save(request);
        if (remembered(client, userId, scopes) && regionAllowed(client, userId)) {
            return issueCode(saved);
        }
        return CONSENT_PAGE + saved.getId();
    }

    /** The consent page data. @throws ApiException 404 OAUTH_REQUEST_EXPIRED, 403 ACCESS_DENIED (other user) */
    @Transactional(readOnly = true)
    public ConsentView consentRequest(UUID requestId) {
        requireEnabled();
        SessionPrincipal principal = currentActor.requirePrincipal();
        AuthorizationRequest request = requireOpen(requestId, principal.userId());
        Client client = clients.findById(request.getClientId()).filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.OAUTH_REQUEST_EXPIRED));
        Set<String> scopes = OAuthEntities.split(request.getScopes());
        return new ConsentView(request, client, scopes, remembered(client, principal.userId(), scopes),
                regionAllowed(client, principal.userId()));
    }

    /**
     * Approves (code, optionally remembered consent) or declines ({@code error=access_denied}) a request; the answer is
     * the absolute redirect location.
     *
     * @throws ApiException 404 OAUTH_REQUEST_EXPIRED, 403 ACCESS_DENIED / OAUTH_REGION_REQUIRED
     */
    @Transactional
    public String decide(UUID requestId, boolean approve, boolean remember) {
        requireEnabled();
        SessionPrincipal principal = currentActor.requirePrincipal();
        AuthorizationRequest request = requireOpen(requestId, principal.userId());
        Client client = clients.findById(request.getClientId()).filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.OAUTH_REQUEST_EXPIRED));
        if (!approve) {
            request.setExpiresAt(clock.instant());
            return error(request.getRedirectUri(), "access_denied", request.getState());
        }
        if (!regionAllowed(client, principal.userId())) {
            throw ApiException.forbidden(ApiErrorCode.OAUTH_REGION_REQUIRED);
        }
        if (remember) {
            Consent consent = consents.findByClientIdAndUserId(client.getId(), principal.userId()).orElseGet(() -> {
                Consent created = new Consent();
                created.setClientId(client.getId());
                created.setUserId(principal.userId());
                created.setScopes("");
                return created;
            });
            Set<String> scopes = new LinkedHashSet<>(OAuthEntities.split(consent.getScopes()));
            scopes.addAll(OAuthEntities.split(request.getScopes()));
            consent.setScopes(OAuthEntities.join(scopes));
            consents.save(consent);
            history.record(history.event(OAuthAction.CONSENT_GRANTED, client.getId())
                    .metadata(Map.of("userId", principal.userId().toString(), "scopes", consent.getScopes()))
                    .build());
        }
        return issueCode(request);
    }

    /** Is the user an active member of one of the client's required regions (or does it have none)? */
    public boolean regionAllowed(Client client, UUID userId) {
        Set<UUID> required = client.requiredRegions();
        if (required.isEmpty()) {
            return true;
        }
        Set<UUID> memberOf = regions.activeRegionIdsOf(userId);
        return required.stream().anyMatch(memberOf::contains);
    }

    private boolean remembered(Client client, UUID userId, Set<String> scopes) {
        return consents.findByClientIdAndUserId(client.getId(), userId)
                .map(c -> OAuthEntities.split(c.getScopes()).containsAll(scopes)).orElse(false);
    }

    private AuthorizationRequest requireOpen(UUID requestId, UUID userId) {
        AuthorizationRequest request = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.OAUTH_REQUEST_EXPIRED));
        if (!request.getUserId().equals(userId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        Instant now = clock.instant();
        boolean answered = !codes.findByRequestIdIn(java.util.List.of(requestId)).isEmpty();
        if (!request.getExpiresAt().isAfter(now) || answered) {
            throw ApiException.notFound(ApiErrorCode.OAUTH_REQUEST_EXPIRED);
        }
        return request;
    }

    private String issueCode(AuthorizationRequest request) {
        String code = OAuthCrypto.randomToken(32);
        Code row = new Code();
        row.setRequestId(request.getId());
        row.setCodeHash(OAuthCrypto.sha256Hex(code));
        row.setExpiresAt(clock.instant().plus(Duration.ofMinutes(properties.getOauth().getCodeMinutes())));
        codes.save(row);
        lastCode = new IssuedCode(code, clients.findById(request.getClientId()).map(Client::getClientId).orElse(""),
                request.getRedirectUri(), request.getState(), clock.instant());
        StringBuilder location = new StringBuilder(request.getRedirectUri())
                .append(request.getRedirectUri().contains("?") ? '&' : '?').append("code=").append(enc(code));
        if (request.getState() != null) {
            location.append("&state=").append(enc(request.getState()));
        }
        return location.toString();
    }

    static String error(String redirectUri, String error, String state) {
        StringBuilder location = new StringBuilder(redirectUri).append(redirectUri.contains("?") ? '&' : '?')
                .append("error=").append(error);
        if (state != null) {
            location.append("&state=").append(enc(state));
        }
        return location.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** The last issued code (e2e helper {@code GET /api/dev/oauth/last-code}; never exposed outside dev profiles). */
    public record IssuedCode(String code, String clientId, String redirectUri, String state, Instant issuedAt) {
    }

    private volatile IssuedCode lastCode;

    public IssuedCode lastIssuedCode() {
        return lastCode;
    }
}
