package network.foodsharing.user.web;

import network.foodsharing.api.PublicUsersApi;
import network.foodsharing.api.model.TokenBody;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class PublicUsersController implements PublicUsersApi {

    @Override
    public ResponseEntity<Void> abortEmailChangeByToken(TokenBody tokenBody) {
        throw ApiException.notImplemented("abortEmailChangeByToken");
    }
}
