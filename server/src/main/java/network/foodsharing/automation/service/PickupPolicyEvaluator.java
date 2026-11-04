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

    /**
     * A policy as the rules see it. {@code gapRuleEnabled}/{@code minimumGapDays}: the store's own gap (this store's
     * pickups only). {@code gapStoreIds}: the other live stores of its shared gap list (decision 151), empty for a
     * store alone; {@code groupMinimumGapDays}: the gap of that list (decision 152), 0 without a list.
     */
    public record Spec(UUID id, UUID storeId, boolean enabled, boolean gapRuleEnabled,
                       int minimumGapDays, boolean cleaningRuleEnabled, boolean experienceRuleEnabled,
                       boolean publicHolidayRuleEnabled, Set<LocalDate> excludedDates, List<LinkRule> links,
                       List<UUID> gapStoreIds, int groupMinimumGapDays) {

        public Spec {
            gapStoreIds = gapStoreIds == null ? List.of() : List.copyOf(gapStoreIds);
        }

        /** A policy without a gap list. */
        public Spec(UUID id, UUID storeId, boolean enabled, boolean gapRuleEnabled,
                    int minimumGapDays, boolean cleaningRuleEnabled, boolean experienceRuleEnabled,
                    boolean publicHolidayRuleEnabled, Set<LocalDate> excludedDates, List<LinkRule> links) {
            this(id, storeId, enabled, gapRuleEnabled, minimumGapDays, cleaningRuleEnabled,
                    experienceRuleEnabled, publicHolidayRuleEnabled, excludedDates, links, List.of(), 0);
        }
    }

    /** A linked-store rule of the policy (own or mirrored; a mirror has before/after swapped). */
    public record LinkRule(List<UUID> storeIds, LinkedStoreWindowMode windowMode, int windowDaysBefore,
                           int windowDaysAfter, int graceMinutes, String message) {
    }

    public record Signup(UUID storeId, UUID userId, Instant pickupAt, boolean confirmed, Instant signedUpAt) {
    }

    /** A future cleaning shift with a free place (FS2). */
    public record CleaningSlot(Instant at, UUID fspId, String fspName) {
    }

    public record UserInfo(UUID id, String firstName, Instant verifiedAt, Locale locale) {
    }

    /** {@code cleaningFspIds}: the region's Fairteiler that count for the cleaning rule (FS2), by name. */
    public record RegionInfo(UUID id, String name, List<UUID> cleaningFspIds) {
    }

    /**
     * The person's own data behind a reason (decision 165 S2), so clients render their own sentences. GAP:
     * {@code lastPickupAt} (the pickup that is too close), {@code storeIds} (its store when another store of the gap
     * list), {@code gapDays}, {@code nextPossibleAt} (start of the first later day that keeps every gap). LINKED_STORE:
     * {@code storeIds}, window, {@code from}/{@code to} (start of the first and the last day of the window).
     * CLEANING_REQUIRED: {@code monthsAgo} (null = never), {@code monthsRequired}, {@code fspId}.
     */
    public record Params(Instant lastPickupAt, Instant nextPossibleAt, Integer gapDays, List<UUID> storeIds,
                         LinkedStoreWindowMode windowMode, Integer before, Integer after, Instant from, Instant to,
                         Integer monthsAgo, Integer monthsRequired, UUID fspId) {

        public static final Params NONE = new Params(null, null, null, List.of(), null, null, null, null, null, null,
                null, null);

        public Params {
            storeIds = storeIds == null ? List.of() : List.copyOf(storeIds);
        }

        static Params gap(Instant lastPickupAt, Instant nextPossibleAt, int gapDays, List<UUID> storeIds) {
            return new Params(lastPickupAt, nextPossibleAt, gapDays, storeIds, null, null, null, null, null, null,
                    null, null);
        }
    }

    /** A rule outcome: the rendered text (audits, mails) and the structured {@link Params} (UI). */
    public record Reason(PolicyRule rule, String text, Params params) {

        public Reason {
            params = params == null ? Params.NONE : params;
        }

        public Reason(PolicyRule rule, String text) {
            this(rule, text, Params.NONE);
        }
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

        /** Whether the user had a confirmed pickup in the store before {@code before} (experience rule). */
        default boolean hasConfirmedPickup(UUID storeId, UUID userId, Instant before) {
            return lastConfirmedPickup(storeId, userId, before).isPresent();
        }

        /** The user's latest confirmed cleaning shift in these Fairteiler before {@code before}. */
        Optional<Instant> lastConfirmedCleaning(Collection<UUID> fspIds, UUID userId, Instant before);

        /** The user's cleaning sign-ups (confirmed or not) in these Fairteiler with {@code from <= at < to}. */
        List<Instant> cleaningSignupsOfUser(UUID userId, Collection<UUID> fspIds, Instant from, Instant to);

        /** Cleaning shifts with a free place across these Fairteiler with {@code from <= at < to}, by time. */
        List<CleaningSlot> freeCleaningSlots(Collection<UUID> fspIds, Instant from, Instant to);

        Optional<String> holidayName(UUID storeId, LocalDate date);

        boolean cleaningExempt(UUID regionId, UUID userId);

        Optional<Spec> enabledPolicy(UUID storeId);

        /** Deadline extended by outages between start and deadline (A26 hook). */
        Instant extendDeadline(Instant start, Instant deadline);

        /**
         * Until when the person may book another linked pickup for this sign-up after the linked one was signed out
         * (decision 165 S3, {@code pickup_link_pending}, extended by outages); empty when nothing is pending.
         */
        default Optional<Instant> linkPendingDeadline(UUID storeId, Instant pickupAt, UUID userId) {
            return Optional.empty();
        }

        /** Absolute link to the cleaning tab of a Fairteiler. */
        String cleaningLink(UUID fspId);

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
        gapReason(policy, storeName, pickupAt, userId, now, zone, t, data).ifPresent(reasons::add);
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
                // Decision 165 S3: a missing linked pickup blocks, unless the linked one was signed out and the
                // person is still within the deadline to book another (pickup_link_pending, Q4).
                Optional<Instant> deadline = data.linkPendingDeadline(storeId, pickupAt, userId);
                if (deadline.isPresent() && now.isBefore(deadline.get())) {
                    pending = new Reason(PolicyRule.LINKED_STORE, t.text("linked-store.pending",
                            t.dateTime(deadline.get(), zone), names, requirement(rule, t)));
                    continue;
                }
                reasons.add(new Reason(PolicyRule.LINKED_STORE,
                        renderLinkedStoreMessage(rule, storeName, names, pickupAt, user.firstName(), zone, t),
                        linkParams(rule, pickupAt, zone)));
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

    /** When the person signed up for {@code pickupAt} here; {@code now} when there is no such sign-up (preview). */
    private static Instant ownSignedUpAt(UUID storeId, Instant pickupAt, UUID userId, Instant now, Data data) {
        return data.signupsOfUser(userId, List.of(storeId), pickupAt, pickupAt.plusMillis(1))
                .stream().map(Signup::signedUpAt).filter(java.util.Objects::nonNull).findFirst().orElse(now);
    }

    /** A pickup of the person that counts for a gap check. */
    private record GapDate(Instant date, UUID storeId) {
    }

    /** One gap check: its minimum and the pickups it compares with. */
    private record GapCheck(int minimum, List<GapDate> dates) {

        Optional<GapDate> firstHit(UUID storeId, Instant pickupAt, ZoneId zone) {
            return dates.stream().filter(d -> calendarGapDays(d.date(), pickupAt, zone) < minimum)
                    .min(Comparator.comparing(GapDate::date)
                            .thenComparing(d -> d.storeId().equals(storeId) ? 0 : 1));
        }

        boolean keeps(LocalDate day, ZoneId zone) {
            return dates.stream().allMatch(d -> calendarGapDays(d.date().atZone(zone).toLocalDate(), day) >= minimum);
        }
    }

    /**
     * The gap rule (decision 152): two independent checks. The own check (this store's pickups, own gap) runs when the
     * own rule is on; the group check (decision 151 D3/D4, group gap) runs whenever the store has another live store
     * in its gap list. One GAP reason: when both fail and the own gap is at most the group gap, the group sentence
     * alone (it already covers this store's pickups); otherwise both sentences. The params name the pickup that is too
     * close and the first later day that keeps both checks.
     */
    private Optional<Reason> gapReason(Spec policy, String storeName, Instant pickupAt, UUID userId, Instant now,
                                       ZoneId zone, PolicyTexts t, Data data) {
        UUID storeId = policy.storeId();
        GapCheck ownCheck = policy.gapRuleEnabled() ? ownGapCheck(policy, pickupAt, userId, now, zone, data) : null;
        GapCheck groupCheck = policy.gapStoreIds().isEmpty() ? null
                : groupGapCheck(policy, pickupAt, userId, now, zone, data);
        Optional<GapDate> ownHit = ownCheck == null ? Optional.empty() : ownCheck.firstHit(storeId, pickupAt, zone);
        Optional<GapDate> groupHit = groupCheck == null ? Optional.empty()
                : groupCheck.firstHit(storeId, pickupAt, zone);
        if (ownHit.isEmpty() && groupHit.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> own = ownHit.map(hit -> t.text("gap", t.days(calendarGapDays(hit.date(), pickupAt, zone)),
                t.days(policy.minimumGapDays())));
        Optional<String> group = groupHit.map(hit -> {
            List<String> names = new ArrayList<>();
            names.add(storeName);
            policy.gapStoreIds().forEach(id -> names.add(data.storeName(id)));
            return t.text("gap.group", t.date(hit.date(), zone), data.storeName(hit.storeId()), t.all(names),
                    t.days(policy.groupMinimumGapDays()), t.days(calendarGapDays(hit.date(), pickupAt, zone)));
        });
        String text;
        GapDate hit;
        int minimum;
        if (own.isPresent() && group.isPresent()) {
            boolean groupOnly = policy.minimumGapDays() <= policy.groupMinimumGapDays();
            text = groupOnly ? group.get() : own.get() + "\n" + group.get();
            hit = groupOnly ? groupHit.get() : ownHit.get();
            minimum = groupOnly ? policy.groupMinimumGapDays() : policy.minimumGapDays();
        } else if (own.isPresent()) {
            text = own.get();
            hit = ownHit.get();
            minimum = policy.minimumGapDays();
        } else {
            text = group.get();
            hit = groupHit.get();
            minimum = policy.groupMinimumGapDays();
        }
        Instant next = nextPossibleDay(pickupAt, zone, ownCheck, groupCheck);
        return Optional.of(new Reason(PolicyRule.GAP, text, Params.gap(hit.date(), next, minimum,
                hit.storeId().equals(storeId) ? List.of() : List.of(hit.storeId()))));
    }

    /** Start of the first day after the pickup's day that keeps every gap check; null beyond the horizon. */
    private static Instant nextPossibleDay(Instant pickupAt, ZoneId zone, GapCheck... checks) {
        LocalDate day = pickupAt.atZone(zone).toLocalDate();
        for (int i = 1; i <= HORIZON.toDays(); i++) {
            LocalDate candidate = day.plusDays(i);
            boolean ok = true;
            for (GapCheck check : checks) {
                if (check != null && !check.keeps(candidate, zone)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return candidate.atStartOfDay(zone).toInstant();
            }
        }
        return null;
    }

    /** Own gap: every other pickup of the person in this store (pending or confirmed) outside its skip window. */
    private static GapCheck ownGapCheck(Spec policy, Instant pickupAt, UUID userId, Instant now, ZoneId zone,
                                        Data data) {
        return new GapCheck(policy.minimumGapDays(), data.signupsOfUser(userId, List.of(policy.storeId()),
                now.minus(Duration.ofDays(3)), now.plus(HORIZON)).stream()
                .map(Signup::pickupAt)
                .filter(date -> !date.equals(pickupAt))
                .filter(date -> !rulesAreSkipped(date, now, zone))
                .map(date -> new GapDate(date, policy.storeId()))
                .toList());
    }

    /**
     * Gap rule with a shared store list (decision 151). This store as without a list (D3: every other own pickup,
     * pending or confirmed, except one at the same time); a pickup in another listed store (D4) counts when it is
     * confirmed, when a sign-up time is missing, or when it was signed up no later than this one (equal times block
     * both); a same-time pickup there counts too. Pickups in their own skip window are ignored (D2). The first blocking
     * pickup by date gives the text. Uses the group gap (decision 152).
     */
    private static GapCheck groupGapCheck(Spec policy, Instant pickupAt, UUID userId, Instant now, ZoneId zone,
                                          Data data) {
        UUID storeId = policy.storeId();
        Instant from = now.minus(Duration.ofDays(3));
        Instant to = now.plus(HORIZON);
        List<GapDate> dates = new ArrayList<>();
        for (Signup own : data.signupsOfUser(userId, List.of(storeId), from, to)) {
            if (!own.pickupAt().equals(pickupAt) && !rulesAreSkipped(own.pickupAt(), now, zone)) {
                dates.add(new GapDate(own.pickupAt(), storeId));
            }
        }
        Instant signedUpAt = ownSignedUpAt(storeId, pickupAt, userId, now, data);
        for (Signup other : data.signupsOfUser(userId, policy.gapStoreIds(), from, to)) {
            if (other.storeId().equals(storeId) || rulesAreSkipped(other.pickupAt(), now, zone)) {
                continue;
            }
            if (other.confirmed() || other.signedUpAt() == null || !other.signedUpAt().isAfter(signedUpAt)) {
                dates.add(new GapDate(other.pickupAt(), other.storeId()));
            }
        }
        return new GapCheck(policy.groupMinimumGapDays(), dates);
    }

    /** Adds the cleaning reason (if any); returns the reason that allows with a message (reminder / new user). */
    private Reason cleaningRule(UUID regionId, String storeName, Instant pickupAt, UserInfo user, Instant now,
                                ZoneId zone, PolicyTexts t, Data data, List<Reason> reasons) {
        RegionInfo region = data.region(regionId);
        List<UUID> fspIds = region.cleaningFspIds() == null ? List.of() : region.cleaningFspIds();
        if (fspIds.isEmpty()) {
            reasons.add(new Reason(PolicyRule.NO_CLEANING_FAIRTEILER, t.text("no-cleaning-fairteiler", region.name())));
            return null;
        }
        long backCheckMonths = Math.max(0, data.cleaningBackCheckMonths());
        Instant threshold = now.atZone(zone).minusMonths(backCheckMonths).toInstant();
        Instant lastCleaning = data.lastConfirmedCleaning(fspIds, user.id(), now).orElse(null);
        boolean past = lastCleaning != null && !lastCleaning.isBefore(threshold);
        // D5: an upcoming sign-up counts even while unconfirmed
        boolean future = !data.cleaningSignupsOfUser(user.id(), fspIds, now, now.plus(HORIZON)).isEmpty();
        if (past || future) {
            return null;
        }
        Instant verifiedAt = user.verifiedAt();
        boolean verificationExempt = verifiedAt != null && !verifiedAt.isAfter(now) && !verifiedAt.isBefore(threshold);
        List<CleaningSlot> freeSlots = data.freeCleaningSlots(fspIds, now, now.plus(CLEANING_SLOT_HORIZON)).stream()
                .filter(slot -> !slot.at().isBefore(now))
                .sorted(Comparator.comparing(CleaningSlot::at)).toList();
        // D6: the next three free shifts across all cleaning Fairteiler as "date – Fairteiler"
        String openSlots = freeSlots.isEmpty() ? t.text("no-open-cleaning-slots")
                : String.join("\n", freeSlots.stream().limit(3)
                        .map(slot -> t.dateTime(slot.at(), zone) + " – " + slot.fspName()).toList());
        // D6: the link goes to the Fairteiler with the earliest free shift, else the first one configured
        String url = data.cleaningLink(freeSlots.isEmpty() ? fspIds.getFirst() : freeSlots.getFirst().fspId());
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
            UUID fspId = freeSlots.isEmpty() ? fspIds.getFirst() : freeSlots.getFirst().fspId();
            reasons.add(new Reason(PolicyRule.CLEANING_REQUIRED, t.text("cleaning-required",
                    t.months(backCheckMonths), historyText, openSlots, t.months(backCheckMonths), url),
                    new Params(null, null, null, List.of(), null, null, null, null, null,
                            lastCleaning == null ? null : (int) monthsAgo(lastCleaning, now, zone),
                            (int) backCheckMonths, fspId)));
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
        return data.hasConfirmedPickup(storeId, userId, now);
    }

    private boolean hasExperiencedCoPickup(UUID storeId, Instant pickupAt, UUID userId, Instant now, Data data) {
        return data.signups(storeId, pickupAt, pickupAt.plusMillis(1)).stream()
                .map(Signup::userId)
                .filter(other -> !other.equals(userId))
                .anyMatch(other -> hasExperience(storeId, other, now, data));
    }

    private record LinkedPickup(UUID storeId, Instant date, boolean confirmed) {
    }

    /** The person's linked pickup that satisfies a link rule ({@code confirmed}: confirmed or already past). */
    public record LinkMatch(UUID storeId, Instant pickupAt, boolean confirmed) {
    }

    /**
     * Per link rule of the policy (in its order) the person's linked pickup matched to {@code pickupAt} — one linked
     * pickup never covers two pickups here — or empty (decision 165 S3: linked options, pending link tracking).
     */
    public List<Optional<LinkMatch>> linkMatches(Spec policy, Instant pickupAt, UUID userId, Data data) {
        Instant now = data.now();
        ZoneId zone = data.zone(policy.storeId());
        List<Optional<LinkMatch>> result = new ArrayList<>();
        for (LinkRule rule : policy.links()) {
            result.add(matchedLinkedPickup(policy.storeId(), rule, pickupAt, userId, now, zone, data).stream()
                    .findFirst().map(l -> new LinkMatch(l.storeId(), l.date(), l.confirmed())));
        }
        return result;
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

    /** The rule's window around the pickup and the stores, as params of a LINKED_STORE reason. */
    static Params linkParams(LinkRule rule, Instant pickupAt, ZoneId zone) {
        LocalDate day = pickupAt.atZone(zone).toLocalDate();
        boolean lastDays = rule.windowMode() == LinkedStoreWindowMode.LAST_DAYS;
        int before = lastDays ? Math.max(0, rule.windowDaysBefore()) : 0;
        int after = lastDays ? Math.max(0, rule.windowDaysAfter()) : 0;
        return new Params(null, null, null, rule.storeIds(), rule.windowMode(), before, after,
                day.minusDays(before).atStartOfDay(zone).toInstant(), day.plusDays(after).atStartOfDay(zone).toInstant(),
                null, null, null);
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
        return calendarGapDays(left.atZone(zone).toLocalDate(), right.atZone(zone).toLocalDate());
    }

    static long calendarGapDays(LocalDate left, LocalDate right) {
        return Math.max(0, Math.abs(ChronoUnit.DAYS.between(left, right)) - 1);
    }

    static long monthsAgo(Instant value, Instant now, ZoneId zone) {
        return ChronoUnit.MONTHS.between(value.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate());
    }

    private static Decision skipped(PolicyRule rule, String text) {
        return new Decision(false, true, false, List.of(new Reason(rule, text)), null);
    }
}
