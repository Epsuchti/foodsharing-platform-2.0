package network.foodsharing.automation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.domain.LinkedStoreWindowMode;
import network.foodsharing.automation.domain.PolicyRule;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * The pickup-policy rules, ported from the reference {@code FoodsharingPickupAutomationService.evaluate()} (A24).
 * Order: excluded date, public holiday, near pickup (≤ 2 calendar days: confirm), gap, experience, cleaning, linked
 * stores. Calendar days use the store's time zone. All data comes through {@link Data}, so the rules are unit-tested
 * without a database.
 */
@Component
public class PickupPolicyEvaluator {

    static final int MINIMUM_GRACE_MINUTES = 20;
    static final long CLEANING_NEW_USER_MESSAGE_AFTER_MONTHS = 6;
    static final Duration HORIZON = Duration.ofDays(400);
    static final Duration CLEANING_SLOT_HORIZON = Duration.ofDays(60);
    static final Set<PolicyRule> ALLOWED = EnumSet.of(PolicyRule.ALL_RULES_PASSED, PolicyRule.CLEANING_REMINDER,
            PolicyRule.CLEANING_NEW_USER);

    /** A policy as the rules see it. */
    public record Spec(UUID id, UUID storeId, boolean enabled, boolean dryRun, boolean gapRuleEnabled,
                       int minimumGapDays, boolean cleaningRuleEnabled, boolean experienceRuleEnabled,
                       boolean publicHolidayRuleEnabled, Set<LocalDate> excludedDates, List<LinkRule> links) {
    }

    /** A linked-store rule of the policy (own or mirrored; a mirror has before/after swapped). */
    public record LinkRule(List<UUID> storeIds, LinkedStoreWindowMode windowMode, int windowDaysBefore,
                           int windowDaysAfter, int graceMinutes, String message) {
    }

    public record Signup(UUID storeId, UUID userId, Instant pickupAt, boolean confirmed, Instant signedUpAt) {
    }

    public record Slot(Instant pickupAt, int occupied) {
    }

    public record UserInfo(UUID id, String firstName, Instant verifiedAt, Locale locale) {
    }

    public record RegionInfo(UUID id, String name, UUID cleaningStoreId) {
    }

    public record Reason(PolicyRule rule, String text) {
    }

    /**
     * Result of one evaluation. {@code skipped}: nothing is done (manual check); {@code deferred}: decided later (no
     * audit). {@code userMessage}: extra message on confirmation (cleaning reminder / new user).
     */
    public record Decision(boolean allowed, boolean skipped, boolean deferred, List<Reason> reasons,
                           String userMessage) {

        public String reasonText() {
            return String.join("\n", reasons.stream().map(Reason::text).toList());
        }
    }

    /** Thrown by {@link Data#holidayName} when the holiday provider cannot answer. */
    public static class HolidayCalendarUnavailableException extends RuntimeException {

        public HolidayCalendarUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Everything the rules read. */
    public interface Data {

        Instant now();

        ZoneId zone(UUID storeId);

        String storeName(UUID storeId);

        UUID regionId(UUID storeId);

        RegionInfo region(UUID regionId);

        UserInfo user(UUID userId);

        /** All sign-ups of the store with {@code from <= pickupAt < to}. */
        List<Signup> signups(UUID storeId, Instant from, Instant to);

        /** The user's sign-ups in the stores with {@code from <= pickupAt < to}. */
        List<Signup> signupsOfUser(UUID userId, Collection<UUID> storeIds, Instant from, Instant to);

        Optional<Instant> lastConfirmedPickup(UUID storeId, UUID userId, Instant before);

        /** Pickups of the store's schedule with {@code from <= pickupAt < to}. */
        List<Slot> slots(UUID storeId, Instant from, Instant to);

        Optional<String> holidayName(UUID storeId, LocalDate date);

        boolean cleaningExempt(UUID regionId, UUID userId);

        Optional<Spec> enabledPolicy(UUID storeId);

        /** Deadline extended by outages between start and deadline (A26 hook). */
        Instant extendDeadline(Instant start, Instant deadline);

        String storeLink(UUID storeId);

        int cleaningBackCheckMonths();

        int minimumFreeCleaningSlots();
    }

    private final MessageSource messages;

    public PickupPolicyEvaluator(MessageSource messages) {
        this.messages = messages;
    }

    public PolicyTexts texts(Locale locale) {
        return new PolicyTexts(messages, locale);
    }

    public Decision evaluate(Spec policy, Instant pickupAt, UUID userId, Data data) {
        return evaluate(policy, pickupAt, userId, data, Set.of(policy.storeId()));
    }

    /**
     * {@code evaluatingStores}: stores whose pending pickup led to this evaluation (linked-store look-ahead); a pending
     * pickup in one of them counts, so mutually linked sign-ups are confirmed together.
     */
    Decision evaluate(Spec policy, Instant pickupAt, UUID userId, Data data, Set<UUID> evaluatingStores) {
        Instant now = data.now();
        UUID storeId = policy.storeId();
        ZoneId zone = data.zone(storeId);
        UserInfo user = data.user(userId);
        PolicyTexts t = texts(user.locale());
        LocalDate pickupDay = pickupAt.atZone(zone).toLocalDate();
        if (policy.excludedDates().contains(pickupDay)) {
            return skipped(PolicyRule.MANUAL_DATE, t.text("manual-date"));
        }
        if (policy.publicHolidayRuleEnabled()) {
            try {
                Optional<String> holiday = data.holidayName(storeId, pickupDay);
                if (holiday.isPresent()) {
                    return skipped(PolicyRule.PUBLIC_HOLIDAY, t.text("public-holiday", holiday.get()));
                }
            } catch (HolidayCalendarUnavailableException ex) {
                return skipped(PolicyRule.HOLIDAY_CALENDAR_UNAVAILABLE, t.text("holiday-calendar-unavailable"));
            }
        }
        if (rulesAreSkipped(pickupAt, now, zone)) {
            return new Decision(true, false, false,
                    List.of(new Reason(PolicyRule.RULES_SKIPPED_NEAR_PICKUP, t.text("rules-skipped-near-pickup"))),
                    null);
        }
        List<Reason> reasons = new ArrayList<>();
        String storeName = data.storeName(storeId);
        if (policy.gapRuleEnabled()) {
            data.signupsOfUser(userId, List.of(storeId), now.minus(Duration.ofDays(3)), now.plus(HORIZON)).stream()
                    .map(Signup::pickupAt)
                    .filter(date -> !date.equals(pickupAt))
                    .filter(date -> !rulesAreSkipped(date, now, zone))
                    .sorted()
                    .map(date -> calendarGapDays(date, pickupAt, zone))
                    .filter(gap -> gap < policy.minimumGapDays())
                    .findFirst()
                    .ifPresent(gap -> reasons.add(new Reason(PolicyRule.GAP,
                            t.text("gap", t.days(gap), t.days(policy.minimumGapDays())))));
        }
        if (policy.experienceRuleEnabled() && !hasExperience(storeId, userId, now, data)
                && !hasExperiencedCoPickup(storeId, pickupAt, userId, now, data)) {
            reasons.add(new Reason(PolicyRule.EXPERIENCE, t.text("experience")));
        }
        Reason cleaningOverride = null;
        UUID regionId = data.regionId(storeId);
        if (policy.cleaningRuleEnabled() && !data.cleaningExempt(regionId, userId)) {
            cleaningOverride = cleaningRule(regionId, storeName, pickupAt, user, now, zone, t, data, reasons);
        }
        Reason pending = null;
        if (!policy.links().isEmpty()) {
            Instant signedUpAt = data.signupsOfUser(userId, List.of(storeId), pickupAt, pickupAt.plusMillis(1))
                    .stream().map(Signup::signedUpAt).filter(java.util.Objects::nonNull).findFirst().orElse(now);
            for (LinkRule rule : policy.links()) {
                String names = t.alternatives(rule.storeIds().stream().map(data::storeName).toList());
                List<LinkedPickup> linked = matchedLinkedPickup(storeId, rule, pickupAt, userId, now, zone, data);
                if (!linked.isEmpty()) {
                    // Both sign-ups have to be approved: wait while the linked pickups are all still pending.
                    if (linked.stream().anyMatch(l -> l.confirmed() || evaluatingStores.contains(l.storeId())
                            || wouldBeConfirmed(l, userId, data, evaluatingStores))) {
                        continue;
                    }
                    pending = new Reason(PolicyRule.LINKED_STORE, t.text("linked-store.awaiting-confirmation", names));
                    continue;
                }
                // Time during which an integration was down does not count (A26 hook).
                Instant deadline = data.extendDeadline(signedUpAt, signedUpAt.plus(
                        Duration.ofMinutes(Math.max(MINIMUM_GRACE_MINUTES, rule.graceMinutes()))));
                if (now.isBefore(deadline)) {
                    pending = new Reason(PolicyRule.LINKED_STORE, t.text("linked-store.pending",
                            t.dateTime(deadline, zone), names, requirement(rule, t)));
                    continue;
                }
                reasons.add(new Reason(PolicyRule.LINKED_STORE,
                        renderLinkedStoreMessage(rule, storeName, names, pickupAt, user.firstName(), zone, t)));
            }
        }
        if (reasons.isEmpty()) {
            reasons.add(new Reason(PolicyRule.ALL_RULES_PASSED, t.text("all-rules-passed")));
        }
        boolean onlyAllowed = reasons.stream().allMatch(reason -> ALLOWED.contains(reason.rule()));
        if (onlyAllowed && pending != null) {
            return new Decision(false, true, true, List.of(pending), null);
        }
        String userMessage = cleaningOverride != null && reasons.contains(cleaningOverride)
                ? cleaningOverride.text() + "\n\n" + t.text("decline-footer") : null;
        return new Decision(onlyAllowed, false, false, List.copyOf(reasons), userMessage);
    }

    /** Adds the cleaning reason (if any); returns the reason that allows with a message (reminder / new user). */
    private Reason cleaningRule(UUID regionId, String storeName, Instant pickupAt, UserInfo user, Instant now,
                                ZoneId zone, PolicyTexts t, Data data, List<Reason> reasons) {
        RegionInfo region = data.region(regionId);
        UUID cleaningStoreId = region.cleaningStoreId();
        if (cleaningStoreId == null) {
            reasons.add(new Reason(PolicyRule.NO_CLEANING_STORE, t.text("no-cleaning-store", region.name())));
            return null;
        }
        long backCheckMonths = Math.max(0, data.cleaningBackCheckMonths());
        Instant threshold = now.atZone(zone).minusMonths(backCheckMonths).toInstant();
        Instant lastCleaning = data.lastConfirmedPickup(cleaningStoreId, user.id(), now).orElse(null);
        boolean past = lastCleaning != null && !lastCleaning.isBefore(threshold);
        boolean future = !data.signupsOfUser(user.id(), List.of(cleaningStoreId), now, now.plus(HORIZON)).isEmpty();
        if (past || future) {
            return null;
        }
        Instant verifiedAt = user.verifiedAt();
        boolean verificationExempt = verifiedAt != null && !verifiedAt.isAfter(now) && !verifiedAt.isBefore(threshold);
        List<Instant> freeSlots = data.slots(cleaningStoreId, now, now.plus(CLEANING_SLOT_HORIZON)).stream()
                .filter(slot -> !slot.pickupAt().isBefore(now))
                .filter(slot -> slot.occupied() == 0)
                .map(Slot::pickupAt).sorted().toList();
        String openSlots = freeSlots.isEmpty() ? t.text("no-open-cleaning-slots")
                : String.join("\n", freeSlots.stream().limit(3).map(at -> t.dateTime(at, zone)).toList());
        String url = data.storeLink(cleaningStoreId);
        if (!verificationExempt) {
            String history = lastCleaning == null ? t.text("no-cleaning-yet")
                    : t.text("reminder-last-cleaning", t.dateTime(lastCleaning, zone));
            Reason reminder = new Reason(PolicyRule.CLEANING_REMINDER, t.text("cleaning-reminder", user.firstName(),
                    t.dateTime(pickupAt, zone), storeName, t.months(backCheckMonths), history,
                    String.valueOf(freeSlots.size()), openSlots, url));
            if (freeSlots.size() < data.minimumFreeCleaningSlots()) {
                reasons.add(reminder);
                return reminder;
            }
            String historyText = lastCleaning == null ? t.text("no-cleaning-yet")
                    : t.text("last-cleaning", t.months(monthsAgo(lastCleaning, now, zone)));
            reasons.add(new Reason(PolicyRule.CLEANING_REQUIRED, t.text("cleaning-required",
                    t.months(backCheckMonths), historyText, openSlots, t.months(backCheckMonths), url)));
            return null;
        }
        if (!verifiedAt.atZone(zone).plusMonths(CLEANING_NEW_USER_MESSAGE_AFTER_MONTHS).toInstant().isAfter(now)) {
            Reason newUser = new Reason(PolicyRule.CLEANING_NEW_USER, t.text("cleaning-new-user", user.firstName(),
                    t.dateTime(pickupAt, zone), storeName, t.months(backCheckMonths), t.dateTime(verifiedAt, zone),
                    t.dateTime(verifiedAt.atZone(zone).plusMonths(backCheckMonths).toInstant(), zone), url));
            reasons.add(newUser);
            return newUser;
        }
        return null;
    }

    private boolean hasExperience(UUID storeId, UUID userId, Instant now, Data data) {
        return data.lastConfirmedPickup(storeId, userId, now).isPresent();
    }

    private boolean hasExperiencedCoPickup(UUID storeId, Instant pickupAt, UUID userId, Instant now, Data data) {
        return data.signups(storeId, pickupAt, pickupAt.plusMillis(1)).stream()
                .map(Signup::userId)
                .filter(other -> !other.equals(userId))
                .anyMatch(other -> hasExperience(storeId, other, now, data));
    }

    private record LinkedPickup(UUID storeId, Instant date, boolean confirmed) {
    }

    /**
     * A pending linked pickup counts once the linked store's own enabled policy would confirm it now; without such a
     * policy the linked pickup has to be confirmed by hand first.
     */
    private boolean wouldBeConfirmed(LinkedPickup linked, UUID userId, Data data, Set<UUID> evaluatingStores) {
        Optional<Spec> linkedPolicy = data.enabledPolicy(linked.storeId());
        if (linkedPolicy.isEmpty()) {
            return false;
        }
        Set<UUID> next = new HashSet<>(evaluatingStores);
        next.add(linked.storeId());
        Decision decision = evaluate(linkedPolicy.get(), linked.date(), userId, data, next);
        return decision.allowed() && !decision.deferred();
    }

    /**
     * Every pickup of the person in this store needs its own linked pickup, so one linked pickup never covers two.
     * Own pickups are matched in order of priority (confirmed first, then earliest sign-up, then date); the result holds
     * the linked pickup matched to {@code pickupAt}, or is empty.
     */
    private List<LinkedPickup> matchedLinkedPickup(UUID storeId, LinkRule rule, Instant pickupAt, UUID userId,
                                                   Instant now, ZoneId zone, Data data) {
        record Own(Instant date, boolean confirmed, Instant signUp) {
        }
        List<Own> own = new ArrayList<>();
        for (Signup signup : data.signupsOfUser(userId, List.of(storeId), now, now.plus(HORIZON))) {
            own.add(new Own(signup.pickupAt(), signup.confirmed(), signup.signedUpAt()));
        }
        if (own.stream().noneMatch(entry -> entry.date().equals(pickupAt))) {
            own.add(new Own(pickupAt, false, null));
        }
        own.sort(Comparator.comparing((Own entry) -> !entry.confirmed())
                .thenComparing(Own::signUp, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Own::date));
        Map<Instant, List<LinkedPickup>> candidates = new HashMap<>();
        for (Own entry : own) {
            candidates.put(entry.date(), linkedStorePickups(rule, entry.date(), userId, now, zone, data));
        }
        Map<String, Instant> owner = new HashMap<>();
        Map<Instant, LinkedPickup> matched = new HashMap<>();
        for (Own entry : own) {
            match(entry.date(), candidates, owner, matched, new HashSet<>());
        }
        LinkedPickup result = matched.get(pickupAt);
        return result == null ? List.of() : List.of(result);
    }

    private boolean match(Instant ownDate, Map<Instant, List<LinkedPickup>> candidates, Map<String, Instant> owner,
                          Map<Instant, LinkedPickup> matched, Set<String> visited) {
        for (LinkedPickup candidate : candidates.getOrDefault(ownDate, List.of())) {
            String key = candidate.storeId() + "@" + candidate.date();
            if (!visited.add(key)) {
                continue;
            }
            Instant holder = owner.get(key);
            if (holder == null || match(holder, candidates, owner, matched, visited)) {
                owner.put(key, ownDate);
                matched.put(ownDate, candidate);
                return true;
            }
        }
        return false;
    }

    /** The user's pickups in the rule's stores on the pickup day or within the window; past pickups count as approved. */
    private List<LinkedPickup> linkedStorePickups(LinkRule rule, Instant pickupAt, UUID userId, Instant now,
                                                  ZoneId zone, Data data) {
        LocalDate day = pickupAt.atZone(zone).toLocalDate();
        boolean lastDays = rule.windowMode() == LinkedStoreWindowMode.LAST_DAYS;
        int before = lastDays ? Math.max(0, rule.windowDaysBefore()) : 0;
        int after = lastDays ? Math.max(0, rule.windowDaysAfter()) : 0;
        Instant from = day.minusDays(before).atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(after + 1L).atStartOfDay(zone).toInstant();
        return data.signupsOfUser(userId, rule.storeIds(), from, to).stream()
                .sorted(Comparator.comparing(Signup::pickupAt))
                .map(s -> new LinkedPickup(s.storeId(), s.pickupAt(), s.confirmed() || s.pickupAt().isBefore(now)))
                .toList();
    }

    static String requirement(LinkRule rule, PolicyTexts t) {
        if (rule.windowMode() != LinkedStoreWindowMode.LAST_DAYS) {
            return t.text("linked-store.requirement.same-day");
        }
        int before = rule.windowDaysBefore();
        int after = rule.windowDaysAfter();
        if (before == after) {
            return before == 1 ? t.text("linked-store.requirement.last-days.one")
                    : t.text("linked-store.requirement.last-days.other", before);
        }
        if (before == 0) {
            return after == 1 ? t.text("linked-store.requirement.after.one")
                    : t.text("linked-store.requirement.after.other", after);
        }
        if (after == 0) {
            return before == 1 ? t.text("linked-store.requirement.before.one")
                    : t.text("linked-store.requirement.before.other", before);
        }
        return t.text("linked-store.requirement.range", before, after);
    }

    static String renderLinkedStoreMessage(LinkRule rule, String storeName, String linkedStoreNames, Instant pickupAt,
                                           String firstName, ZoneId zone, PolicyTexts t) {
        String template = rule.message() == null || rule.message().isBlank()
                ? t.text("linked-store.default-message") : rule.message();
        return template.strip()
                .replace("{{name}}", firstName == null ? "" : firstName.strip())
                .replace("{{storeName}}", storeName == null ? "" : storeName.strip())
                .replace("{{linkedStoreName}}", linkedStoreNames)
                .replace("{{pickupDate}}", t.longDateTime(pickupAt, zone))
                .replace("{{requirement}}", requirement(rule, t))
                .replace("{{graceMinutes}}", String.valueOf(Math.max(MINIMUM_GRACE_MINUTES, rule.graceMinutes())));
    }

    /** From the start of the day two calendar days before the pickup, the rules are skipped. */
    static boolean rulesAreSkipped(Instant pickupAt, Instant now, ZoneId zone) {
        Instant from = pickupAt.atZone(zone).toLocalDate().minusDays(2).atStartOfDay(zone).toInstant();
        return !now.isBefore(from);
    }

    static long calendarGapDays(Instant left, Instant right, ZoneId zone) {
        LocalDate leftDate = left.atZone(zone).toLocalDate();
        LocalDate rightDate = right.atZone(zone).toLocalDate();
        return Math.max(0, Math.abs(ChronoUnit.DAYS.between(leftDate, rightDate)) - 1);
    }

    static long monthsAgo(Instant value, Instant now, ZoneId zone) {
        return ChronoUnit.MONTHS.between(value.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate());
    }

    private static Decision skipped(PolicyRule rule, String text) {
        return new Decision(false, true, false, List.of(new Reason(rule, text)), null);
    }
}
