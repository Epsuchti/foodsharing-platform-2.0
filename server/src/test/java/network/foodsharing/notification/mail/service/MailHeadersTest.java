package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import network.foodsharing.notification.mail.OutgoingMail;
import org.junit.jupiter.api.Test;

/** Extra MIME headers stored with outbox mails (List-Unsubscribe, N7). */
class MailHeadersTest {

    @Test
    void storedLinesBecomeHeaders() {
        assertThat(MailDeliveryService.headers("List-Unsubscribe: <https://x/newsletter/unsubscribe/a.b>\nX-Y: z"))
                .containsExactly(Map.entry("List-Unsubscribe", "<https://x/newsletter/unsubscribe/a.b>"),
                        Map.entry("X-Y", "z"));
        assertThat(MailDeliveryService.headers(null)).isEmpty();
        assertThat(new OutgoingMail("a", "b", "c", "d", null).headers()).isEmpty();
    }
}
