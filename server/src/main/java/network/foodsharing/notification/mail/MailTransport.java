package network.foodsharing.notification.mail;

/**
 * Delivers one rendered mail. {@code SmtpMailTransport} ({@code app.mail.transport=smtp}) in production/local; the
 * capturing transport ({@code app.mail.transport=capture}) in e2e. Throwing means "retry later" for the outbox.
 */
public interface MailTransport {

    void send(OutgoingMail mail);
}
