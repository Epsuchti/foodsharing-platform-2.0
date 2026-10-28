package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import network.foodsharing.auth.domain.EmailChange;
import org.junit.jupiter.api.Test;

/** Email change rules (A5): redacted history values, 24 h validity, single use. */
class EmailChangeServiceTest {

    @Test
    void historyValuesAreRedacted() {
        assertThat(EmailChangeService.redact("anna@example.org")).isEqualTo("a***@example.org");
        assertThat(EmailChangeService.redact("broken")).isEqualTo("***");
        assertThat(EmailChangeService.redact(null)).isNull();
    }

    @Test
    void aChangeIsPendingFor24HoursUntilCompletedOrAborted() {
        Instant requested = Instant.parse("2026-11-02T09:00:00Z");
        EmailChange change = new EmailChange();
        change.setExpiresAt(requested.plus(EmailChangeService.VALIDITY));
        assertThat(change.isPendingAt(requested.plusSeconds(23 * 3600))).isTrue();
        assertThat(change.isPendingAt(requested.plusSeconds(24 * 3600))).isFalse();
        change.setCompletedAt(requested);
        assertThat(change.isPendingAt(requested)).isFalse();
        change.setCompletedAt(null);
        change.setAbortedAt(requested);
        assertThat(change.isPendingAt(requested)).isFalse();
    }
}
