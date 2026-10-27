package network.foodsharing.notification.mail;

/** A rendered mail ready for a {@link MailTransport}; {@code htmlBody} may be null (text only). */
public record OutgoingMail(String to, String from, String subject, String textBody, String htmlBody) {
}
