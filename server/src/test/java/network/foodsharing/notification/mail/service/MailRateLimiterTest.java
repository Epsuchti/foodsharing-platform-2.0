package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A27: hour and day windows with a fixed "now", deferral instead of dropping, duplicate suppression, AUTH bypass. */
class MailRateLimiterTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private MailOutboxRepository repository;
    private MailRateLimiter limiter;

    @BeforeEach
    void setUp() {
        repository = mock(MailOutboxRepository.class);
        AppProperties properties = new AppProperties();
        limiter = new MailRateLimiter(repository, properties);
        when(repository.slotsSince(anyString(), any())).thenReturn(List.of());
        when(repository.templateSlotsSince(anyString(), anyString(), any())).thenReturn(List.of());
    }

    private static MailDraft draft(String identifier) {
        return new MailDraft("a@example.org", "pickup/removed", null, Map.of(), UUID.randomUUID(), "PICKUPS",
                identifier);
    }

    private static List<Instant> slots(int count, Instant first, Duration step) {
        List<Instant> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(first.plus(step.multipliedBy(i)));
        }
        return slots;
    }

    @Test
    void underTheLimitsTheMailGoesNow() {
        when(repository.slotsSince(eq("a@example.org"), eq(NOW.minus(Duration.ofHours(1)))))
                .thenReturn(slots(19, NOW.minus(Duration.ofMinutes(50)), Duration.ofMinutes(1)));
        assertThat(limiter.check(draft(null), NOW).outcome()).isEqualTo(MailRateLimiter.Outcome.ALLOW);
    }

    @Test
    void the21stMailWithinAnHourIsDeferredNotDropped() {
        // 20 mails at 08:10, 08:11, …, 08:29 → the 21st may go once 08:10 left the window: 09:10
        when(repository.slotsSince(eq("a@example.org"), eq(NOW.minus(Duration.ofHours(1)))))
                .thenReturn(slots(20, NOW.minus(Duration.ofMinutes(50)), Duration.ofMinutes(1)));
        MailRateLimiter.Decision decision = limiter.check(draft(null), NOW);
        assertThat(decision.outcome()).isEqualTo(MailRateLimiter.Outcome.DEFER);
        assertThat(decision.until()).isEqualTo(Instant.parse("2026-11-02T09:10:00Z"));
    }

    @Test
    void deferredMailsQueueBehindEachOther() {
        // 20 in the window plus one already deferred to 09:10 → the next one waits for the 2nd slot (08:11) + 1 h
        List<Instant> used = slots(20, NOW.minus(Duration.ofMinutes(50)), Duration.ofMinutes(1));
        used.add(Instant.parse("2026-11-02T09:10:00Z"));
        when(repository.slotsSince(eq("a@example.org"), any())).thenReturn(used);
        assertThat(limiter.check(draft(null), NOW).until()).isEqualTo(Instant.parse("2026-11-02T09:11:00Z"));
    }

    @Test
    void sixthMailOfOneTemplatePerDayIsDeferredToTheNextDayWindow() {
        when(repository.templateSlotsSince(eq("a@example.org"), eq("pickup/removed"), eq(NOW.minus(Duration.ofDays(1)))))
                .thenReturn(slots(5, NOW.minus(Duration.ofHours(20)), Duration.ofHours(1)));
        MailRateLimiter.Decision decision = limiter.check(draft(null), NOW);
        assertThat(decision.outcome()).isEqualTo(MailRateLimiter.Outcome.DEFER);
        assertThat(decision.until()).isEqualTo(NOW.minus(Duration.ofHours(20)).plus(Duration.ofDays(1)));
    }

    @Test
    void duplicateWithinTenMinutesIsSuppressed() {
        when(repository.existsByToAddressAndTemplateAndIdentifierAndCreatedAtAfter("a@example.org", "pickup/removed",
                "PICKUP-1", NOW.minus(Duration.ofMinutes(10)))).thenReturn(true);
        assertThat(limiter.check(draft("PICKUP-1"), NOW).outcome()).isEqualTo(MailRateLimiter.Outcome.SUPPRESS);
        assertThat(limiter.check(draft("PICKUP-2"), NOW).outcome()).isEqualTo(MailRateLimiter.Outcome.ALLOW);
    }

    @Test
    void authMailsAreNeverLimited() {
        MailOutboxRepository untouched = mock(MailOutboxRepository.class);
        MailRateLimiter authLimiter = new MailRateLimiter(untouched, new AppProperties());
        MailDraft magicLink = new MailDraft("a@example.org", "magic-link", null, Map.of());
        assertThat(magicLink.category()).isEqualTo(MailDraft.AUTH);
        assertThat(authLimiter.check(magicLink, NOW).outcome()).isEqualTo(MailRateLimiter.Outcome.ALLOW);
        verifyNoInteractions(untouched);
    }
}
