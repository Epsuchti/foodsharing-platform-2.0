package network.foodsharing.mailbox.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import network.foodsharing.api.PublicMailboxApi;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.InboundMail;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.mailbox.service.IncomingMailProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound mail webhook of the mail provider (MB5, A35): secret path, CSRF-exempt, 404 on a wrong or unset secret,
 * 202 once stored (idempotent: a retried delivery is recognised by its raw hash).
 */
@RestController
public class PublicMailboxController implements PublicMailboxApi {

    private final IncomingMailProcessor processor;
    private final AppProperties properties;

    public PublicMailboxController(IncomingMailProcessor processor, AppProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    @Override
    public ResponseEntity<Void> receiveInboundMail(String secret, InboundMail inboundMail) {
        String expected = properties.getMailbox().getWebhookSecret();
        if (expected == null || expected.isBlank() || secret == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND);
        }
        byte[] raw = inboundMail.getRawMime();
        if (raw == null || raw.length == 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        try {
            processor.process(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        return ResponseEntity.accepted().build();
    }
}
