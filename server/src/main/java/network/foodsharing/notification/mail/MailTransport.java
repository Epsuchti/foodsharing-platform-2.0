package network.foodsharing.notification.mail;

import org.springframework.modulith.NamedInterface;

/**
 * Delivers one rendered mail. Implementations: {@link CapturingMailTransport} ({@code app.mail.transport=capture},
 * e2e and tests) and the SMTP transport ({@code app.mail.transport=smtp}, owned by the outbox). Only the mail outbox
 * calls this; everything else enqueues mails in the outbox.
 */
@NamedInterface("mail-transport")
public interface MailTransport {

    /**
     * Sends the mail synchronously.
     *
     * @param to       recipient address
     * @param subject  subject line
     * @param textBody plain text body (required)
     * @param htmlBody HTML body, may be {@code null}
     * @throws RuntimeException when delivery failed (the outbox retries with backoff)
     */
    void send(String to, String subject, String textBody, String htmlBody);
}
