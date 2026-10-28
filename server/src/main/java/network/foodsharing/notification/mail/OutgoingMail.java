package network.foodsharing.notification.mail;

import java.util.Map;

/**
 * A rendered mail ready for a {@link MailTransport}; {@code htmlBody} may be null (text only); {@code headers} are
 * extra MIME headers (e.g. {@code List-Unsubscribe} for newsletters).
 */
public record OutgoingMail(String to, String from, String subject, String textBody, String htmlBody,
                           Map<String, String> headers) {

    public OutgoingMail {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public OutgoingMail(String to, String from, String subject, String textBody, String htmlBody) {
        this(to, from, subject, textBody, htmlBody, Map.of());
    }
}
