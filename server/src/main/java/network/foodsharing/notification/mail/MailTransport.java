package network.foodsharing.notification.mail;

/**
 * Delivers one rendered mail. {@code SmtpMailTransport} ({@code app.mail.transport=smtp}) in production/local;
 * {@link CapturingMailTransport} ({@code app.mail.transport=capture}) in e2e and tests. Only the mail outbox calls
 * this; throwing means "retry later".
 */
public interface MailTransport {

    void send(OutgoingMail mail);
}
