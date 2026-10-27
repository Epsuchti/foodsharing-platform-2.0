package network.foodsharing.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import network.foodsharing.config.MutableClock;
import org.junit.jupiter.api.Test;

class CapturingMailTransportTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final CapturingMailTransport transport = new CapturingMailTransport(
            new MutableClock(java.time.Clock.fixed(NOW, ZoneOffset.UTC), null));

    @Test
    void keepsMailsNewestFirstAndFiltersByRecipient() {
        transport.send("a@local", "first", "one", null);
        transport.send("B@Local", "second", "two", "<p>two</p>");

        assertThat(transport.list(null)).extracting(CapturedMail::subject).containsExactly("second", "first");
        assertThat(transport.list("b@local")).extracting(CapturedMail::subject).containsExactly("second");
        assertThat(transport.list(" ")).hasSize(2);
        assertThat(transport.list(null).getFirst().sentAt()).isEqualTo(NOW);

        transport.clear();
        assertThat(transport.list(null)).isEmpty();
    }

    @Test
    void extractsLinksFromTextAndHtmlWithoutDuplicates() {
        String text = "Log in: http://localhost:8081/login/verify?token=abc&returnUrl=%2Fdashboard\nThanks";
        String html = "<a href=\"http://localhost:8081/login/verify?token=abc&amp;returnUrl=%2Fdashboard\">Log in</a>"
                + " <a href=\"mailto:help@local\">help</a> <a href='https://foodsharing.network/faq'>FAQ</a>";
        assertThat(CapturingMailTransport.extractLinks(text, html)).containsExactly(
                "http://localhost:8081/login/verify?token=abc&returnUrl=%2Fdashboard",
                "https://foodsharing.network/faq");
        assertThat(CapturingMailTransport.extractLinks(null, null)).isEqualTo(List.of());
    }

    @Test
    void dropsTheOldestMailsBeyondTheLimit() {
        for (int i = 0; i <= CapturingMailTransport.MAX_MAILS; i++) {
            transport.send("x@local", "mail " + i, "", null);
        }
        List<CapturedMail> mails = transport.list(null);
        assertThat(mails).hasSize(CapturingMailTransport.MAX_MAILS);
        assertThat(mails.getFirst().subject()).isEqualTo("mail " + CapturingMailTransport.MAX_MAILS);
        assertThat(mails.getLast().subject()).isEqualTo("mail 1");
    }
}
