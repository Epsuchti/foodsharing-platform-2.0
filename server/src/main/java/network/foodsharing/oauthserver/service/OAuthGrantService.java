package network.foodsharing.oauthserver.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.CleanupTask;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.oauthserver.domain.OAuthAction;
import network.foodsharing.oauthserver.domain.OAuthEntities;
import network.foodsharing.oauthserver.domain.OAuthEntities.AuthorizationRequest;
import network.foodsharing.oauthserver.domain.OAuthEntities.Client;
import network.foodsharing.oauthserver.domain.OAuthEntities.Consent;
import network.foodsharing.oauthserver.domain.OAuthEntities.RefreshToken;
import network.foodsharing.oauthserver.repository.OAuthClientRepository;
import network.foodsharing.oauthserver.repository.OAuthCodeRepository;
import network.foodsharing.oauthserver.repository.OAuthConsentRepository;
import network.foodsharing.oauthserver.repository.OAuthRefreshTokenRepository;
import network.foodsharing.oauthserver.repository.OAuthRequestRepository;
import network.foodsharing.user.service.ConnectionsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user's grants (C13): connected apps = remembered consents plus clients with a live refresh token
 * ({@code /account/connections}); revoking removes the consent and revokes the tokens ({@code CONSENT_REVOKED}).
 * Also the cleanup of expired requests, codes and refresh tokens and the account deletion (A40).
 */
@Service
public class OAuthGrantService implements ConnectionsPort, CleanupTask {

    static final Duration GRACE = Duration.ofDays(1);

    private final OAuthClientRepository clients;
    private final OAuthConsentRepository consents;
    private final OAuthRefreshTokenRepository refreshTokens;
    private final OAuthRequestRepository requests;
    private final OAuthCodeRepository codes;
    private final HistoryService history;
    private final Clock clock;

    public OAuthGrantService(OAuthClientRepository clients, OAuthConsentRepository consents,
                             OAuthRefreshTokenRepository refreshTokens, OAuthRequestRepository requests,
                             OAuthCodeRepository codes, HistoryService history, Clock clock) {
        this.clients = clients;
        this.consents = consents;
        this.refreshTokens = refreshTokens;
        this.requests = requests;
        this.codes = codes;
        this.history = history;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Connection> list(UUID userId) {
        Map<UUID, Connection> byClient = new LinkedHashMap<>();
        for (Consent consent : consents.findByUserId(userId)) {
            clients.findById(consent.getClientId()).filter(c -> c.getDeletedAt() == null).ifPresent(c ->
                    byClient.put(c.getId(), new Connection(c.getClientId(), c.getName(),
                            OAuthEntities.split(consent.getScopes()), consent.getCreatedAt())));
        }
        for (RefreshToken token : refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId,
                clock.instant())) {
            if (byClient.containsKey(token.getClientId())) {
                continue;
            }
            clients.findById(token.getClientId()).filter(c -> c.getDeletedAt() == null).ifPresent(c ->
                    byClient.put(c.getId(), new Connection(c.getClientId(), c.getName(),
                            OAuthEntities.split(token.getScopes()), token.getCreatedAt())));
        }
        return new ArrayList<>(byClient.values());
    }

    @Override
    @Transactional
    public void revoke(UUID userId, String clientId) {
        Client client = clients.findByClientIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.OAUTH_CLIENT_NOT_FOUND));
        boolean had = false;
        for (Consent consent : consents.findByUserId(userId)) {
            if (consent.getClientId().equals(client.getId())) {
                consents.delete(consent);
                had = true;
            }
        }
        for (RefreshToken token : refreshTokens.findByUserIdAndClientIdAndRevokedAtIsNull(userId, client.getId())) {
            token.setRevokedAt(clock.instant());
            had = true;
        }
        if (!had) {
            throw ApiException.notFound(ApiErrorCode.OAUTH_CLIENT_NOT_FOUND);
        }
        history.record(history.event(OAuthAction.CONSENT_REVOKED, client.getId())
                .metadata(Map.of("userId", userId.toString())).build());
    }

    /** A40: consents, refresh tokens and pending requests/codes of the account are deleted. */
    @Transactional
    public void removeUser(UUID userId) {
        for (Consent consent : consents.findByUserId(userId)) {
            history.record(history.event(OAuthAction.CONSENT_REVOKED, consent.getClientId())
                    .reason("account deleted").build());
            consents.delete(consent);
        }
        refreshTokens.deleteAll(refreshTokens.findByUserId(userId));
        List<AuthorizationRequest> pending = requests.findByUserId(userId);
        codes.deleteAll(codes.findByRequestIdIn(pending.stream().map(AuthorizationRequest::getId).toList()));
        requests.deleteAll(pending);
    }

    @Override
    public String name() {
        return "oauth";
    }

    @Override
    @Transactional
    @NoHistory(reason = "retention housekeeping of expired authorization requests, codes and refresh tokens")
    public int cleanup(Instant now) {
        Instant cutoff = now.minus(GRACE);
        List<AuthorizationRequest> expired = requests.findByExpiresAtBefore(cutoff);
        int count = expired.size();
        codes.deleteAll(codes.findByRequestIdIn(expired.stream().map(AuthorizationRequest::getId).toList()));
        requests.deleteAll(expired);
        List<RefreshToken> tokens = refreshTokens.findByExpiresAtBefore(cutoff);
        refreshTokens.deleteAll(tokens);
        return count + tokens.size();
    }

    /** Consents of the user (data export). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> export(UUID userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Connection c : list(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientId", c.clientId());
            row.put("clientName", c.clientName());
            row.put("scopes", List.copyOf(c.scopes()));
            row.put("grantedAt", String.valueOf(c.grantedAt()));
            out.add(row);
        }
        return out;
    }
}
