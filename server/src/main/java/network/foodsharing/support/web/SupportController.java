package network.foodsharing.support.web;

import network.foodsharing.api.SupportApi;
import network.foodsharing.api.model.SupportTicketCreate;
import network.foodsharing.api.model.SupportTicketCreated;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class SupportController implements SupportApi {

    @Override
    public ResponseEntity<SupportTicketCreated> createSupportTicket(SupportTicketCreate supportTicketCreate) {
        throw ApiException.notImplemented("createSupportTicket");
    }
}
