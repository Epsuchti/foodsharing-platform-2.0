package network.foodsharing.oauthserver.web;

import java.util.List;
import network.foodsharing.api.AdminOauthApi;
import network.foodsharing.api.model.OAuthClient;
import network.foodsharing.api.model.OAuthClientCreate;
import network.foodsharing.api.model.OAuthClientCreated;
import network.foodsharing.api.model.OAuthClientSecret;
import network.foodsharing.api.model.OAuthClientUpdate;
import network.foodsharing.api.model.OAuthGrantType;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.oauthserver.service.OAuthClientService;
import network.foodsharing.oauthserver.service.OAuthClientService.ClientInput;
import network.foodsharing.oauthserver.service.OAuthClientService.ClientView;
import network.foodsharing.region.mapper.RegionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** OAuth client administration (C13, ORGA). */
@RestController
public class AdminOAuthClientsController implements AdminOauthApi {

    private final OAuthClientService clients;
    private final RegionMapper regionMapper;

    public AdminOAuthClientsController(OAuthClientService clients, RegionMapper regionMapper) {
        this.clients = clients;
        this.regionMapper = regionMapper;
    }

    @Override
    public ResponseEntity<OAuthClientCreated> createOAuthClient(OAuthClientCreate in) {
        OAuthClientService.Created created = clients.create(new ClientInput(in.getClientId(), in.getName(),
                in.getConfidential(), in.getRedirectUris(), in.getScopes(), grants(in.getGrantTypes()),
                in.getRequiredRegionIds(), in.getEnabled()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new OAuthClientCreated().client(toApi(created.client())).clientSecret(created.secret()));
    }

    @Override
    public ResponseEntity<Void> deleteOAuthClient(String clientId) {
        clients.delete(clientId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<OAuthClient> getOAuthClient(String clientId) {
        return ResponseEntity.ok(toApi(clients.get(clientId)));
    }

    @Override
    public ResponseEntity<List<OAuthClient>> listOAuthClients() {
        return ResponseEntity.ok(clients.list().stream().map(this::toApi).toList());
    }

    @Override
    public ResponseEntity<OAuthClientSecret> rotateOAuthClientSecret(String clientId) {
        return ResponseEntity.ok(new OAuthClientSecret().clientSecret(clients.rotateSecret(clientId)));
    }

    @Override
    public ResponseEntity<OAuthClient> updateOAuthClient(String clientId, OAuthClientUpdate in) {
        return ResponseEntity.ok(toApi(clients.update(clientId, new ClientInput(null, in.getName(),
                in.getConfidential(), in.getRedirectUris(), in.getScopes(), grants(in.getGrantTypes()),
                in.getRequiredRegionIds(), in.getEnabled()))));
    }

    private static List<String> grants(List<OAuthGrantType> types) {
        return types == null ? null : types.stream().map(OAuthGrantType::getValue).toList();
    }

    private OAuthClient toApi(ClientView view) {
        var c = view.client();
        return new OAuthClient().clientId(c.getClientId()).name(c.getName()).confidential(c.isConfidential())
                .redirectUris(c.redirectUriList()).scopes(List.copyOf(c.scopeSet()))
                .grantTypes(c.grantTypeSet().stream().map(OAuthGrantType::fromValue).toList())
                .requiredRegionIds(List.copyOf(c.requiredRegions()))
                .requiredRegions(view.requiredRegions().stream().map(regionMapper::toRef).toList())
                .enabled(c.isEnabled())
                .createdAt(TimeMapper.toOffset(c.getCreatedAt())).consentCount((int) view.consentCount());
    }
}
