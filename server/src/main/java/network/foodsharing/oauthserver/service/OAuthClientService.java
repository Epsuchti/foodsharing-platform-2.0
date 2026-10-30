package network.foodsharing.oauthserver.service;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.oauthserver.domain.OAuthAction;
import network.foodsharing.oauthserver.domain.OAuthEntities;
import network.foodsharing.oauthserver.domain.OAuthEntities.Client;
import network.foodsharing.oauthserver.domain.OAuthScope;
import network.foodsharing.oauthserver.repository.OAuthClientRepository;
import network.foodsharing.oauthserver.repository.OAuthConsentRepository;
import network.foodsharing.oauthserver.repository.OAuthRefreshTokenRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client administration (C13, ORGA only): client ids {@code [a-z0-9._-]{3,60}}, exact absolute redirect URIs (https,
 * or http for localhost), scopes from {@link OAuthScope} (openid always), grants authorization_code [+ refresh_token].
 * Confidential clients get a 48-character secret that is shown once and stored as SHA-256.
 */
@Service
public class OAuthClientService {

    public static final Set<String> GRANT_TYPES = Set.of("authorization_code", "refresh_token");
    static final int SECRET_LENGTH = 48;

    public record ClientInput(String clientId, String name, Boolean confidential, List<String> redirectUris,
                              List<String> scopes, List<String> grantTypes, List<UUID> requiredRegionIds,
                              Boolean enabled) {
    }

    /** {@code requiredRegions}: the client's required regions in their stored order (deleted ones left out). */
    public record ClientView(Client client, long consentCount, List<Region> requiredRegions) {
    }

    public record Created(ClientView client, String secret) {
    }

    private final OAuthClientRepository clients;
    private final OAuthConsentRepository consents;
    private final OAuthRefreshTokenRepository refreshTokens;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final Clock clock;

    public OAuthClientService(OAuthClientRepository clients, OAuthConsentRepository consents,
                              OAuthRefreshTokenRepository refreshTokens, RegionService regions,
                              CurrentActorService currentActor, PermissionService permissions,
                              HistoryService history, Clock clock) {
        this.clients = clients;
        this.consents = consents;
        this.refreshTokens = refreshTokens;
        this.regions = regions;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClientView> list() {
        requireOrga();
        return clients.findByDeletedAtIsNullOrderByName().stream()
                .map(c -> view(c, consents.countByClientId(c.getId()))).toList();
    }

    /** @throws ApiException 403, 404 OAUTH_CLIENT_NOT_FOUND */
    @Transactional(readOnly = true)
    public ClientView get(String clientId) {
        requireOrga();
        Client client = require(clientId);
        return view(client, consents.countByClientId(client.getId()));
    }

    /** The regions a client requires (id + name for the admin list and the consent page). */
    static List<Region> requiredRegions(Client client, RegionService regions) {
        List<UUID> ids = List.copyOf(client.requiredRegions());
        return inOrder(ids, regions.findAll(ids));
    }

    /** {@code found} in the order of {@code ids}; ids without a region (deleted) are skipped. */
    public static List<Region> inOrder(List<UUID> ids, List<Region> found) {
        Map<UUID, Region> byId = new java.util.HashMap<>();
        found.forEach(region -> byId.put(region.getId(), region));
        return ids.stream().distinct().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private ClientView view(Client client, long consentCount) {
        return new ClientView(client, consentCount, requiredRegions(client, regions));
    }

    /** @throws ApiException 403, 409 OAUTH_CLIENT_EXISTS, 400 VALIDATION_FAILED / OAUTH_REDIRECT_URI_INVALID */
    @Transactional
    public Created create(ClientInput in) {
        requireOrga();
        String clientId = in.clientId() == null ? "" : in.clientId().trim();
        if (!clientId.matches("[a-z0-9._-]{3,60}")) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (clients.existsByClientId(clientId)) {
            throw ApiException.conflict(ApiErrorCode.OAUTH_CLIENT_EXISTS);
        }
        Client client = new Client();
        client.setClientId(clientId);
        apply(client, in, true);
        String secret = null;
        if (client.isConfidential()) {
            secret = OAuthCrypto.randomSecret(SECRET_LENGTH);
            client.setSecretHash(OAuthCrypto.sha256Hex(secret));
        }
        Client saved = clients.save(client);
        history.record(history.event(OAuthAction.CLIENT_CREATED, saved.getId())
                .metadata(Map.of("clientId", clientId, "confidential", saved.isConfidential())).build());
        return new Created(view(saved, 0), secret);
    }

    /**
     * Confidential → public drops the secret; public → confidential is refused (a PATCH never hands out a secret,
     * create a new confidential client instead).
     *
     * @throws ApiException 403, 404 OAUTH_CLIENT_NOT_FOUND, 400 (VALIDATION_FAILED with {@code field=confidential}
     *                      for public → confidential)
     */
    @Transactional
    public ClientView update(String clientId, ClientInput in) {
        requireOrga();
        Client client = require(clientId);
        if (!mayChangeKind(client.isConfidential(), in.confidential())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "confidential");
        }
        boolean wasConfidential = client.isConfidential();
        String before = describe(client);
        apply(client, in, false);
        if (wasConfidential && !client.isConfidential()) {
            client.setSecretHash(null);
        }
        history.record(history.event(OAuthAction.CLIENT_UPDATED, client.getId())
                .change("client", before, describe(client)).build());
        return view(client, consents.countByClientId(client.getId()));
    }

    /** May a PATCH set the client kind to {@code requested} (null = unchanged)? Only public → confidential is refused. */
    public static boolean mayChangeKind(boolean confidential, Boolean requested) {
        return confidential || !Boolean.TRUE.equals(requested);
    }

    /** Soft delete; consents and refresh tokens are revoked. @throws ApiException 403, 404 */
    @Transactional
    public void delete(String clientId) {
        requireOrga();
        Client client = require(clientId);
        client.setDeletedAt(clock.instant());
        client.setEnabled(false);
        consents.deleteAll(consents.findByClientId(client.getId()));
        refreshTokens.findByClientIdAndRevokedAtIsNull(client.getId()).forEach(t -> t.setRevokedAt(clock.instant()));
        history.record(history.event(OAuthAction.CLIENT_DELETED, client.getId())
                .metadata(Map.of("clientId", clientId)).build());
    }

    /** New secret, shown once. @throws ApiException 403, 404, 409 OAUTH_CLIENT_PUBLIC */
    @Transactional
    public String rotateSecret(String clientId) {
        requireOrga();
        Client client = require(clientId);
        if (!client.isConfidential()) {
            throw ApiException.conflict(ApiErrorCode.OAUTH_CLIENT_PUBLIC);
        }
        String secret = OAuthCrypto.randomSecret(SECRET_LENGTH);
        client.setSecretHash(OAuthCrypto.sha256Hex(secret));
        history.record(history.event(OAuthAction.SECRET_ROTATED, client.getId()).build());
        return secret;
    }

    /** Is the redirect URI acceptable for registration (absolute, https or http on localhost, no fragment)? */
    public static boolean validRedirectUri(String value) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getFragment() != null || uri.getHost() == null) {
                return false;
            }
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            return "https".equals(scheme) || ("http".equals(scheme) && local);
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private void apply(Client client, ClientInput in, boolean creating) {
        if (in.name() != null) {
            if (in.name().isBlank() || in.name().length() > 200) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            client.setName(in.name().trim());
        }
        if (in.confidential() != null) {
            client.setConfidential(in.confidential());
        }
        if (in.redirectUris() != null) {
            if (in.redirectUris().isEmpty() || in.redirectUris().size() > 10) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            for (String uri : in.redirectUris()) {
                if (uri == null || !validRedirectUri(uri.trim()) || uri.trim().contains(" ")) {
                    throw ApiException.badRequest(ApiErrorCode.OAUTH_REDIRECT_URI_INVALID);
                }
            }
            client.setRedirectUris(OAuthEntities.join(in.redirectUris().stream().map(String::trim).distinct().toList()));
        }
        if (in.scopes() != null) {
            Set<String> scopes = new LinkedHashSet<>();
            scopes.add(OAuthScope.openid.name());
            for (String scope : in.scopes()) {
                if (!knownScope(scope)) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
                }
                scopes.add(scope);
            }
            client.setScopes(OAuthEntities.join(scopes));
        }
        if (in.grantTypes() != null) {
            if (!GRANT_TYPES.containsAll(in.grantTypes()) || !in.grantTypes().contains("authorization_code")) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            client.setGrantTypes(OAuthEntities.join(new LinkedHashSet<>(in.grantTypes())));
        }
        if (in.requiredRegionIds() != null) {
            in.requiredRegionIds().forEach(regions::require);
            client.setRequiredRegionIds(OAuthEntities.join(new LinkedHashSet<>(in.requiredRegionIds())));
        }
        if (in.enabled() != null) {
            client.setEnabled(in.enabled());
        }
        if (creating && (client.getName() == null || client.getRedirectUris() == null || client.getScopes() == null
                || client.getGrantTypes() == null)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
    }

    static boolean knownScope(String scope) {
        for (OAuthScope known : OAuthScope.values()) {
            if (known.name().equals(scope)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Client c) {
        return c.getName() + "|" + c.isConfidential() + "|" + c.getRedirectUris() + "|" + c.getScopes() + "|"
                + c.getGrantTypes() + "|" + c.getRequiredRegionIds() + "|" + c.isEnabled();
    }

    private Client require(String clientId) {
        return clients.findByClientIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.OAUTH_CLIENT_NOT_FOUND));
    }

    private void requireOrga() {
        if (!permissions.isOrga(currentActor.requirePrincipal())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
