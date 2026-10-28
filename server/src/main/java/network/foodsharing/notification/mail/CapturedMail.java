package network.foodsharing.notification.mail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A mail kept in memory by {@link CapturingMailTransport}; {@code links} are the URLs found in text and HTML;
 * {@code from} and {@code attachmentNames} let tests assert mailbox mails (A35). */
@NamedInterface("mail-transport")
public record CapturedMail(UUID id, String to, String subject, String text, String html, List<String> links,
                           Instant sentAt, String from, List<String> attachmentNames) {

    public CapturedMail {
        links = List.copyOf(links);
        attachmentNames = attachmentNames == null ? List.of() : List.copyOf(attachmentNames);
    }
}
