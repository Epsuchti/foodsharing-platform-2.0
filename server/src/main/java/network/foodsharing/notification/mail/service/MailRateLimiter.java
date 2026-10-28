package network.foodsharing.notification.mail.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import org.springframework.stereotype.Component;

/**
 * Per-recipient mail limits (A27): at most {@code per-user-per-hour} mails per recipient in any hour and
 * {@code per-template-per-user-per-day} of one template per day. Mails beyond a limit are deferred to the first free
 * slot (never dropped); a mail with the same template and identifier as one queued within
 * {@code duplicate-window-minutes} is suppressed. AUTH mails (sign-in) are never limited.
 */
@Component
public class MailRateLimiter {

    /** ALLOW (send now), DEFER (send at {@code until}) or SUPPRESS (duplicate, do not queue). */
    public enum Outcome { ALLOW, DEFER, SUPPRESS }

    public record Decision(Outcome outcome, Instant until) {

        static Decision allow() {
            return new Decision(Outcome.ALLOW, null);
        }
    }

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final MailOutboxRepository repository;
    private final AppProperties.Mail.RateLimit limits;

    public MailRateLimiter(MailOutboxRepository repository, AppProperties properties) {
        this.repository = repository;
        this.limits = properties.getMail().getRateLimit();
    }

    public Decision check(MailDraft draft, Instant now) {
        if (MailDraft.AUTH.equals(draft.category())) {
            return Decision.allow();
        }
        if (draft.identifier() != null && repository.existsByToAddressAndTemplateAndIdentifierAndCreatedAtAfter(
                draft.to(), draft.template(), draft.identifier(),
                now.minus(Duration.ofMinutes(limits.getDuplicateWindowMinutes())))) {
            return new Decision(Outcome.SUPPRESS, null);
        }
        Instant hourly = firstFreeSlot(repository.slotsSince(draft.to(), now.minus(HOUR)), limits.getPerUserPerHour(),
                HOUR, now);
        Instant daily = firstFreeSlot(repository.templateSlotsSince(draft.to(), draft.template(), now.minus(DAY)),
                limits.getPerTemplatePerUserPerDay(), DAY, now);
        Instant until = hourly.isAfter(daily) ? hourly : daily;
        return until.isAfter(now) ? new Decision(Outcome.DEFER, until) : Decision.allow();
    }

    /**
     * Slots ({@code slots}, ascending, all within or after the window ending now) already used: the next mail may go
     * out once fewer than {@code limit} slots lie in the window before it, i.e. one {@code window} after the slot that
     * is {@code limit} places before the end.
     */
    static Instant firstFreeSlot(List<Instant> slots, int limit, Duration window, Instant now) {
        if (limit <= 0 || slots.size() < limit) {
            return now;
        }
        Instant candidate = slots.get(slots.size() - limit).plus(window);
        return candidate.isAfter(now) ? candidate : now;
    }
}
