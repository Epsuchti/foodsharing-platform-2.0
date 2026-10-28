package network.foodsharing.support.web;

import network.foodsharing.api.SupportApi;
import network.foodsharing.api.model.SupportTicketCreate;
import network.foodsharing.api.model.SupportTicketCreated;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.support.service.SupportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Support form of logged-in users (C9). */
@RestController
public class SupportController implements SupportApi {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    @Override
    public ResponseEntity<SupportTicketCreated> createSupportTicket(SupportTicketCreate body) {
        var ticket = support.create(new SupportService.TicketInput(body.getSubject(), body.getBody(), body.getPage(),
                body.getUserAgent(), body.getAttachmentUploadIds(), null, null),
                HttpExchange.clientIp(HttpExchange.request()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new SupportTicketCreated().ticketId(ticket.getId()));
    }
}
