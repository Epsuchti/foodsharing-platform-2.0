package network.foodsharing.oauthserver.web;

import java.util.List;
import network.foodsharing.api.AdminOauthApi;
import network.foodsharing.api.model.OAuthClient;
import network.foodsharing.api.model.OAuthClientCreate;
import network.foodsharing.api.model.OAuthClientCreated;
import network.foodsharing.api.model.OAuthClientSecret;
import network.foodsharing.api.model.OAuthClientUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class AdminOAuthClientsController implements AdminOauthApi {

    @Override
    public ResponseEntity<OAuthClientCreated> createOAuthClient(OAuthClientCreate oauthClientCreate) {
        throw ApiException.notImplemented("createOAuthClient");
    }

    @Override
    public ResponseEntity<Void> deleteOAuthClient(String clientId) {
        throw ApiException.notImplemented("deleteOAuthClient");
    }

    @Override
    public ResponseEntity<OAuthClient> getOAuthClient(String clientId) {
        throw ApiException.notImplemented("getOAuthClient");
    }

    @Override
    public ResponseEntity<List<OAuthClient>> listOAuthClients() {
        throw ApiException.notImplemented("listOAuthClients");
    }

    @Override
    public ResponseEntity<OAuthClientSecret> rotateOAuthClientSecret(String clientId) {
        throw ApiException.notImplemented("rotateOAuthClientSecret");
    }

    @Override
    public ResponseEntity<OAuthClient> updateOAuthClient(String clientId, OAuthClientUpdate oauthClientUpdate) {
        throw ApiException.notImplemented("updateOAuthClient");
    }
}
