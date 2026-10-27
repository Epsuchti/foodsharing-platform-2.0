package network.foodsharing.notification.mail;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

/**
 * Keeps sent mails in memory instead of delivering them ({@code app.mail.transport=capture}: the e2e and test
 * profiles). The dev API serves them at {@code GET /api/dev/mails} so Playwright can follow magic links.
 */
@Component
@NamedInterface("mail-transport")
@ConditionalOnProperty(prefix = "app.mail", name = "transport", havingValue = "capture")
public class CapturingMailTransport implements MailTransport {

    /** Oldest mails are dropped beyond this many. */
    static final int MAX_MAILS = 500;

    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>()]+");
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final Deque<CapturedMail> mails = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    public CapturingMailTransport(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void send(OutgoingMail outgoing) {
        send(outgoing.to(), outgoing.subject(), outgoing.textBody(), outgoing.htmlBody());
    }

    /** Captures a mail directly (tests). */
    public void send(String to, String subject, String textBody, String htmlBody) {
        CapturedMail mail = new CapturedMail(UUID.randomUUID(), to, subject, textBody == null ? "" : textBody, htmlBody,
                extractLinks(textBody, htmlBody), clock.instant());
        mails.addFirst(mail);
        while (mails.size() > MAX_MAILS) {
            mails.pollLast();
        }
    }

    /** Newest first; {@code to} filters by recipient (case-insensitive), {@code null} returns all. */
    public List<CapturedMail> list(String to) {
        String wanted = to == null || to.isBlank() ? null : to.strip().toLowerCase(Locale.ROOT);
        List<CapturedMail> result = new ArrayList<>();
        for (CapturedMail mail : mails) {
            if (wanted == null || (mail.to() != null && mail.to().toLowerCase(Locale.ROOT).equals(wanted))) {
                result.add(mail);
            }
        }
        return result;
    }

    public void clear() {
        mails.clear();
    }

    static List<String> extractLinks(String text, String html) {
        Set<String> links = new LinkedHashSet<>();
        if (html != null) {
            Matcher href = HREF.matcher(html);
            while (href.find()) {
                String link = href.group(1).replace("&amp;", "&");
                if (link.startsWith("http://") || link.startsWith("https://")) {
                    links.add(link);
                }
            }
        }
        for (String source : new String[] {text, html}) {
            if (source == null) {
                continue;
            }
            Matcher url = URL.matcher(source);
            while (url.find()) {
                links.add(url.group().replace("&amp;", "&"));
            }
        }
        return List.copyOf(links);
    }
}
