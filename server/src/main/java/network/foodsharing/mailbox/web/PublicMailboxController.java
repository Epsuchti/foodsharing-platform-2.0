package network.foodsharing.mailbox.web;

import network.foodsharing.api.PublicMailboxApi;
import network.foodsharing.api.model.InboundMail;
import network.foodsharing.api.model.Mail;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound mail webhook (MB5). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class PublicMailboxController implements PublicMailboxApi {

    @Override
    public ResponseEntity<Void> receiveInboundMail(String secret, InboundMail inboundMail) {
        throw ApiException.notImplemented("receiveInboundMail");
    }
}
