package network.foodsharing.notification.mail.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An already composed mail (no template) for the outbox, e.g. a mailbox mail sent from {@code fromAddress} (A35).
 * {@code headers} carry {@code Message-ID}, {@code In-Reply-To}, {@code References}; {@code attachmentUploadIds} are
 * streamed from the upload store when the outbox delivers the mail.
 */
public record DirectMail(String to, String fromAddress, String fromName, String replyTo, String subject,
                         String textBody, String htmlBody, Map<String, String> headers,
                         List<UUID> attachmentUploadIds, UUID senderUserId) {

    public static final String TEMPLATE = "direct";

    public DirectMail {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        attachmentUploadIds = attachmentUploadIds == null ? List.of() : List.copyOf(attachmentUploadIds);
    }
}
