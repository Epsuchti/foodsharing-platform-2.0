package network.foodsharing.user.web;

import network.foodsharing.api.PublicUsersApi;
import network.foodsharing.api.model.TokenBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public user operations: aborting an email change with the link mailed to the old address (A5). */
@RestController
public class PublicUsersController implements PublicUsersApi {

    private final network.foodsharing.user.service.EmailChangePort emailChange;

    public PublicUsersController(network.foodsharing.user.service.EmailChangePort emailChange) {
        this.emailChange = emailChange;
    }

    @Override
    public ResponseEntity<Void> abortEmailChangeByToken(TokenBody tokenBody) {
        emailChange.abortByToken(tokenBody.getToken());
        return ResponseEntity.noContent().build();
    }
}
