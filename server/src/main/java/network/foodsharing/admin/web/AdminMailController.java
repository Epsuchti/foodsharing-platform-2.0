package network.foodsharing.admin.web;

import java.util.UUID;
import network.foodsharing.api.AdminMailApi;
import network.foodsharing.api.model.MailOutboxItem;
import network.foodsharing.api.model.MailOutboxPage;
import network.foodsharing.api.model.MailOutboxStatus;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mail outbox administration (ORGA, M4 B3).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class AdminMailController implements AdminMailApi {

    @Override
    public ResponseEntity<MailOutboxPage> listMailOutbox(MailOutboxStatus status, String search, Integer page, Integer size) {
        throw ApiException.notImplemented("listMailOutbox");
    }

    @Override
    public ResponseEntity<MailOutboxItem> retryMail(UUID mailId) {
        throw ApiException.notImplemented("retryMail");
    }
}
