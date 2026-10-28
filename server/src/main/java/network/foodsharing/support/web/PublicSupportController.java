package network.foodsharing.support.web;

import network.foodsharing.api.PublicSupportApi;
import network.foodsharing.api.model.PublicSupportTicketCreate;
import network.foodsharing.api.model.SupportTicketCreated;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class PublicSupportController implements PublicSupportApi {

    @Override
    public ResponseEntity<SupportTicketCreated> createPublicSupportTicket(PublicSupportTicketCreate publicSupportTicketCreate) {
        throw ApiException.notImplemented("createPublicSupportTicket");
    }
}
