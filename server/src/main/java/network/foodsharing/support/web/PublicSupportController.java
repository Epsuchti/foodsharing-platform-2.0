package network.foodsharing.support.web;

import network.foodsharing.api.PublicSupportApi;
import network.foodsharing.api.model.PublicSupportTicketCreate;
import network.foodsharing.api.model.SupportTicketCreated;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.support.service.SupportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public support form (no login, C9): rate limited per IP. */
@RestController
public class PublicSupportController implements PublicSupportApi {

    private final SupportService support;

    public PublicSupportController(SupportService support) {
        this.support = support;
    }

    @Override
    public ResponseEntity<SupportTicketCreated> createPublicSupportTicket(PublicSupportTicketCreate body) {
        var ticket = support.createPublic(new SupportService.TicketInput(body.getSubject(), body.getBody(),
                body.getPage(), body.getUserAgent(), null, body.getEmail(), body.getFirstName()),
                HttpExchange.clientIp(HttpExchange.request()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new SupportTicketCreated().ticketId(ticket.getId()));
    }
}
