package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
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
import network.foodsharing.automation.service.PickupPolicyEvaluator.Decision;
import network.foodsharing.automation.service.PickupPolicyEvaluator.LinkRule;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Reason;
import network.foodsharing.automation.service.PickupPolicyEvaluator.RegionInfo;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Signup;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Slot;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.automation.service.PickupPolicyEvaluator.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/** One test per rule and branch of the reference evaluate() (M4 B4). Now = Monday 2026-11-02 10:00 Zurich. */
class PickupPolicyEvaluatorTest {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.randomUUID();
    static final UUID LINKED = UUID.randomUUID();
    static final UUID CLEANING = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    FakeData data;
    PickupPolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/automation/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        evaluator = new PickupPolicyEvaluator(messages);
        data = new FakeData();
    }

    static Instant at(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(ZURICH).toInstant();
    }

    static Spec policy(boolean gap, int minGap, boolean cleaning, boolean experience, boolean holiday,
                       Set<LocalDate> excluded, List<LinkRule> links) {
        return new Spec(UUID.randomUUID(), STORE, true, false, gap, minGap, cleaning, experience, holiday, excluded,
                links);
    }

    static Spec plain() {
        return policy(false, 0, false, false, false, Set.of(), List.of());
    }

    static List<PolicyRule> rules(Decision decision) {
        return decision.reasons().stream().map(Reason::rule).toList();
    }

    // ---------------------------------------------------------------- skip rules

    @Test
    void excludedDateSkips() {
        Decision d = evaluator.evaluate(policy(false, 0, false, false, false, Set.of(LocalDate.parse("2026-11-09")),
                List.of()), at("2026-11-09T19:00"), USER, data);
        assertThat(d.skipped()).isTrue();
        assertThat(d.allowed()).isFalse();
        assertThat(rules(d)).containsExactly(PolicyRule.MANUAL_DATE);
    }

    @Test
    void publicHolidaySkipsWithName() {
        data.holidays.put(LocalDate.parse("2026-12-25"), "Weihnachten");
        Decision d = evaluator.evaluate(policy(false, 0, false, false, true, Set.of(), List.of()),
                at("2026-12-25T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.PUBLIC_HOLIDAY);
        assertThat(d.reasons().getFirst().text()).contains("Weihnachten");
        assertThat(d.skipped()).isTrue();
    }

    @Test
    void holidayCalendarUnavailableSkips() {
        data.holidayDown = true;
        Decision d = evaluator.evaluate(policy(false, 0, false, false, true, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.HOLIDAY_CALENDAR_UNAVAILABLE);
        assertThat(d.skipped()).isTrue();
    }

    @Test
    void holidayRuleOffIgnoresHolidays() {
        data.holidays.put(LocalDate.parse("2026-12-25"), "Weihnachten");
        assertThat(evaluator.evaluate(plain(), at("2026-12-25T19:00"), USER, data).allowed()).isTrue();
    }

    @Test
    void rulesAreSkippedFromTheStartOfTheDayTwoDaysBefore() {
        Spec strict = policy(false, 0, false, true, false, Set.of(), List.of());
        Decision near = evaluator.evaluate(strict, at("2026-11-04T23:00"), USER, data);
        assertThat(near.allowed()).isTrue();
        assertThat(rules(near)).containsExactly(PolicyRule.RULES_SKIPPED_NEAR_PICKUP);
        Decision far = evaluator.evaluate(strict, at("2026-11-05T00:30"), USER, data);
        assertThat(rules(far)).containsExactly(PolicyRule.EXPERIENCE);
    }

    @Test
    void skipWindowUsesLocalMidnightAcrossDst() {
        // DST ends 2026-10-25 in Zurich: pickup Tue 27 Oct 00:30 local, skip from Sun 25 Oct 00:00 local (+02:00).
        Instant pickup = at("2026-10-27T00:30");
        assertThat(PickupPolicyEvaluator.rulesAreSkipped(pickup, Instant.parse("2026-10-24T21:59:59Z"), ZURICH))
                .isFalse();
        assertThat(PickupPolicyEvaluator.rulesAreSkipped(pickup, Instant.parse("2026-10-24T22:00:00Z"), ZURICH))
                .isTrue();
    }

    // ---------------------------------------------------------------- gap

    @Test
    void gapBelowMinimumDeclines() {
        data.signups.add(new Signup(STORE, USER, at("2026-11-10T19:00"), true, NOW.minusSeconds(3600)));
        Decision d = evaluator.evaluate(policy(true, 2, false, false, false, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isFalse();
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).contains("1 Tag").contains("2 Tage");
    }

    @Test
    void gapAtMinimumPasses() {
        data.signups.add(new Signup(STORE, USER, at("2026-11-10T19:00"), false, NOW));
        Decision d = evaluator.evaluate(policy(true, 2, false, false, false, Set.of(), List.of()),
                at("2026-11-13T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.ALL_RULES_PASSED);
    }

    @Test
    void gapIgnoresSignupsInTheirOwnSkipWindowAndOtherStores() {
        data.signups.add(new Signup(STORE, USER, at("2026-11-03T19:00"), true, NOW));
        data.signups.add(new Signup(LINKED, USER, at("2026-11-06T19:00"), true, NOW));
        Decision d = evaluator.evaluate(policy(true, 3, false, false, false, Set.of(), List.of()),
                at("2026-11-05T19:00"), USER, data);
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void calendarGapCountsDaysInBetween() {
        assertThat(PickupPolicyEvaluator.calendarGapDays(at("2026-11-10T23:30"), at("2026-11-11T00:30"), ZURICH))
                .isZero();
        assertThat(PickupPolicyEvaluator.calendarGapDays(at("2026-11-10T08:00"), at("2026-11-13T20:00"), ZURICH))
                .isEqualTo(2);
    }

    // ---------------------------------------------------------------- experience

    @Test
    void experienceMissingDeclines() {
        Decision d = evaluator.evaluate(policy(false, 0, false, true, false, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.EXPERIENCE);
    }

    @Test
    void ownPastConfirmedPickupIsExperience() {
        data.signups.add(new Signup(STORE, USER, at("2026-10-01T19:00"), true, NOW.minus(Duration.ofDays(40))));
        assertThat(evaluator.evaluate(policy(false, 0, false, true, false, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data).allowed()).isTrue();
    }

    @Test
    void experiencedCoPickupPasses() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, OTHER, at("2026-10-01T19:00"), true, NOW));
        data.signups.add(new Signup(STORE, OTHER, pickup, true, NOW));
        assertThat(evaluator.evaluate(policy(false, 0, false, true, false, Set.of(), List.of()), pickup, USER, data)
                .allowed()).isTrue();
    }

    // ---------------------------------------------------------------- cleaning

    Spec cleaning() {
        return policy(false, 0, true, false, false, Set.of(), List.of());
    }

    void freeCleaningSlots(int count) {
        for (int i = 0; i < count; i++) {
            data.slots.add(new Slot(at("2026-11-10T18:00").plus(Duration.ofDays(i)), 0));
        }
        data.slots.add(new Slot(at("2026-11-09T18:00"), 1));
    }

    @Test
    void noCleaningStoreDeclines() {
        data.cleaningStore = null;
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.NO_CLEANING_STORE);
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void exemptUserSkipsTheCleaningRule() {
        data.exempt = true;
        assertThat(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data).allowed()).isTrue();
    }

    @Test
    void cleaningWithinTwelveMonthsPasses() {
        data.signups.add(new Signup(CLEANING, USER, at("2026-03-01T10:00"), true, NOW));
        assertThat(rules(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data)))
                .containsExactly(PolicyRule.ALL_RULES_PASSED);
    }

    @Test
    void plannedCleaningPasses() {
        data.signups.add(new Signup(CLEANING, USER, at("2026-11-20T10:00"), false, NOW));
        assertThat(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data).allowed()).isTrue();
    }

    @Test
    void fewFreeCleaningSlotsAllowWithReminder() {
        data.signups.add(new Signup(CLEANING, USER, at("2025-06-01T10:00"), true, NOW));
        freeCleaningSlots(3);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isTrue();
        assertThat(rules(d)).containsExactly(PolicyRule.CLEANING_REMINDER);
        assertThat(d.userMessage()).startsWith("Hey Sarah").contains("01.06.2025").contains("nur noch 3 freie")
                .contains("10.11.2026 18:00").contains("/stores/" + CLEANING)
                .endsWith("wende dich an die Betriebsverantwortlichen.");
    }

    @Test
    void enoughFreeCleaningSlotsDecline() {
        freeCleaningSlots(4);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isFalse();
        assertThat(rules(d)).containsExactly(PolicyRule.CLEANING_REQUIRED);
        assertThat(d.reasons().getFirst().text()).contains("12 Monaten").contains("noch keinen Fairteiler");
        assertThat(d.userMessage()).isNull();
    }

    @Test
    void newUserAfterSixMonthsIsReminded() {
        data.verifiedAt = at("2026-03-01T10:00");
        freeCleaningSlots(5);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isTrue();
        assertThat(rules(d)).containsExactly(PolicyRule.CLEANING_NEW_USER);
        assertThat(d.userMessage()).contains("01.03.2026").contains("01.03.2027");
    }

    @Test
    void newUserWithinSixMonthsPassesSilently() {
        data.verifiedAt = at("2026-09-01T10:00");
        freeCleaningSlots(5);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.ALL_RULES_PASSED);
        assertThat(d.userMessage()).isNull();
    }

    @Test
    void reminderWithAnotherDeclineStillDeclines() {
        freeCleaningSlots(1);
        Decision d = evaluator.evaluate(policy(false, 0, true, true, false, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isFalse();
        assertThat(rules(d)).containsExactly(PolicyRule.EXPERIENCE, PolicyRule.CLEANING_REMINDER);
    }

    // ---------------------------------------------------------------- linked stores

    static LinkRule sameDay(int grace, String message) {
        return new LinkRule(List.of(LINKED), LinkedStoreWindowMode.SAME_DAY, 0, 0, grace, message);
    }

    Spec linked(LinkRule rule) {
        return policy(false, 0, false, false, false, Set.of(), List.of(rule));
    }

    @Test
    void confirmedLinkedPickupOnTheSameDayPasses() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(2))));
        data.signups.add(new Signup(LINKED, USER, at("2026-11-12T09:00"), true, NOW));
        assertThat(evaluator.evaluate(linked(sameDay(20, null)), pickup, USER, data).allowed()).isTrue();
    }

    @Test
    void pendingLinkedPickupWithoutPolicyDefers() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(2))));
        data.signups.add(new Signup(LINKED, USER, at("2026-11-12T09:00"), false, NOW));
        Decision d = evaluator.evaluate(linked(sameDay(20, null)), pickup, USER, data);
        assertThat(d.deferred()).isTrue();
        assertThat(d.reasons().getFirst().text()).contains("bestätigt ist");
    }

    @Test
    void missingLinkedPickupDefersWithinGrace() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofMinutes(10))));
        Decision d = evaluator.evaluate(linked(sameDay(30, null)), pickup, USER, data);
        assertThat(d.deferred()).isTrue();
        assertThat(rules(d)).containsExactly(PolicyRule.LINKED_STORE);
        assertThat(d.reasons().getFirst().text()).contains("Wartet bis 02.11.2026 10:20").contains("Linked AG")
                .contains("am selben Tag");
    }

    @Test
    void graceIsAtLeastTwentyMinutesAndExtendedByOutages() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofMinutes(25))));
        data.outage = Duration.ofMinutes(10);
        assertThat(evaluator.evaluate(linked(sameDay(5, null)), pickup, USER, data).deferred()).isTrue();
    }

    @Test
    void missingLinkedPickupAfterGraceDeclinesWithRenderedMessage() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(1))));
        Decision d = evaluator.evaluate(linked(sameDay(30,
                "Hi {{name}}: {{storeName}} braucht {{linkedStoreName}} {{requirement}} ({{pickupDate}}, {{graceMinutes}})")),
                pickup, USER, data);
        assertThat(d.allowed()).isFalse();
        assertThat(d.deferred()).isFalse();
        assertThat(d.reasons().getFirst().text())
                .isEqualTo("Hi Sarah: Store GmbH braucht Linked AG am selben Tag (Donnerstag, 12.11.2026 19:00, 30)");
    }

    @Test
    void defaultMessageNamesAllAlternatives() {
        Instant pickup = at("2026-11-12T19:00");
        UUID third = UUID.randomUUID();
        data.names.put(third, "Dritt GmbH");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(1))));
        Decision d = evaluator.evaluate(linked(new LinkRule(List.of(LINKED, third), LinkedStoreWindowMode.SAME_DAY, 0,
                0, 20, null)), pickup, USER, data);
        assertThat(d.reasons().getFirst().text()).startsWith("Hallo Sarah").contains("Linked AG oder Dritt GmbH");
    }

    @Test
    void lastDaysWindowAndPastPickupsCountAsConfirmed() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(1))));
        data.signups.add(new Signup(LINKED, USER, at("2026-11-10T19:00"), false, NOW));
        LinkRule window = new LinkRule(List.of(LINKED), LinkedStoreWindowMode.LAST_DAYS, 2, 0, 20, null);
        assertThat(evaluator.evaluate(linked(window), pickup, USER, data).deferred()).isTrue();
        data.signups.clear();
        Instant soon = at("2026-11-05T19:00");
        data.signups.add(new Signup(STORE, USER, soon, false, NOW.minus(Duration.ofHours(1))));
        data.signups.add(new Signup(LINKED, USER, at("2026-11-01T19:00"), false, NOW.minus(Duration.ofDays(5))));
        LinkRule back = new LinkRule(List.of(LINKED), LinkedStoreWindowMode.LAST_DAYS, 4, 0, 20, null);
        assertThat(evaluator.evaluate(linked(back), soon, USER, data).allowed()).isTrue();
    }

    @Test
    void oneLinkedPickupNeverCoversTwoOwnPickups() {
        Instant first = at("2026-11-12T09:00");
        Instant second = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, first, true, NOW.minus(Duration.ofHours(3))));
        data.signups.add(new Signup(STORE, USER, second, false, NOW.minus(Duration.ofHours(1))));
        data.signups.add(new Signup(LINKED, USER, at("2026-11-12T12:00"), true, NOW));
        Decision d = evaluator.evaluate(linked(sameDay(20, null)), second, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.LINKED_STORE);
        assertThat(d.allowed()).isFalse();
    }

    @Test
    void mutuallyLinkedPendingSignupsAreConfirmedTogether() {
        Instant pickup = at("2026-11-12T19:00");
        Instant linkedPickup = at("2026-11-12T09:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(1))));
        data.signups.add(new Signup(LINKED, USER, linkedPickup, false, NOW.minus(Duration.ofHours(1))));
        Spec mirror = new Spec(UUID.randomUUID(), LINKED, true, false, false, 0, false, false, false, Set.of(),
                List.of(new LinkRule(List.of(STORE), LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null)));
        data.policies.put(LINKED, mirror);
        // the recursion stops at the evaluating store (guard), so both sides would be confirmed
        assertThat(evaluator.evaluate(linked(sameDay(20, null)), pickup, USER, data).allowed()).isTrue();
        // a linked policy that would decline keeps this one waiting
        data.policies.put(LINKED, new Spec(mirror.id(), LINKED, true, false, false, 0, false, true, false, Set.of(),
                mirror.links()));
        assertThat(evaluator.evaluate(linked(sameDay(20, null)), pickup, USER, data).deferred()).isTrue();
    }

    @Test
    void allRulesPassed() {
        Decision d = evaluator.evaluate(plain(), at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isTrue();
        assertThat(rules(d)).containsExactly(PolicyRule.ALL_RULES_PASSED);
    }

    @Test
    void requirementTexts() {
        PolicyTexts t = evaluator.texts(Locale.GERMAN);
        assertThat(PickupPolicyEvaluator.requirement(sameDay(20, null), t)).isEqualTo("am selben Tag");
        assertThat(PickupPolicyEvaluator.requirement(new LinkRule(List.of(), LinkedStoreWindowMode.LAST_DAYS, 1, 1,
                20, null), t)).isEqualTo("am selben Tag, am Tag davor oder am Tag danach");
        assertThat(PickupPolicyEvaluator.requirement(new LinkRule(List.of(), LinkedStoreWindowMode.LAST_DAYS, 0, 3,
                20, null), t)).isEqualTo("am selben Tag oder innerhalb von 3 Tagen nach dieser Abholung");
        assertThat(PickupPolicyEvaluator.requirement(new LinkRule(List.of(), LinkedStoreWindowMode.LAST_DAYS, 1, 0,
                20, null), t)).isEqualTo("am selben Tag oder am Tag davor");
        assertThat(PickupPolicyEvaluator.requirement(new LinkRule(List.of(), LinkedStoreWindowMode.LAST_DAYS, 2, 5,
                20, null), t)).isEqualTo("am selben Tag, bis zu 2 Tage davor oder bis zu 5 Tage danach");
        assertThat(evaluator.texts(Locale.ENGLISH).text("linked-store.requirement.same-day")).isEqualTo("on the same day");
    }

    /** In-memory data. */
    static class FakeData implements PickupPolicyEvaluator.Data {

        final List<Signup> signups = new ArrayList<>();
        final List<Slot> slots = new ArrayList<>();
        final Map<LocalDate, String> holidays = new HashMap<>();
        final Map<UUID, Spec> policies = new HashMap<>();
        final Map<UUID, String> names = new HashMap<>(Map.of(STORE, "Store GmbH", LINKED, "Linked AG", CLEANING,
                "Fairteiler"));
        boolean holidayDown;
        boolean exempt;
        UUID cleaningStore = CLEANING;
        Instant verifiedAt = Instant.parse("2020-01-01T00:00:00Z");
        Duration outage = Duration.ZERO;

        @Override
        public Instant now() {
            return NOW;
        }

        @Override
        public ZoneId zone(UUID storeId) {
            return ZURICH;
        }

        @Override
        public String storeName(UUID storeId) {
            return names.getOrDefault(storeId, "?");
        }

        @Override
        public UUID regionId(UUID storeId) {
            return REGION;
        }

        @Override
        public RegionInfo region(UUID regionId) {
            return new RegionInfo(REGION, "Zürich", cleaningStore);
        }

        @Override
        public UserInfo user(UUID userId) {
            return new UserInfo(userId, "Sarah", verifiedAt, Locale.GERMAN);
        }

        @Override
        public List<Signup> signups(UUID storeId, Instant from, Instant to) {
            return signups.stream().filter(s -> s.storeId().equals(storeId) && !s.pickupAt().isBefore(from)
                    && s.pickupAt().isBefore(to)).toList();
        }

        @Override
        public List<Signup> signupsOfUser(UUID userId, Collection<UUID> storeIds, Instant from, Instant to) {
            Set<UUID> ids = new HashSet<>(storeIds);
            return signups.stream().filter(s -> s.userId().equals(userId) && ids.contains(s.storeId())
                    && !s.pickupAt().isBefore(from) && s.pickupAt().isBefore(to)).toList();
        }

        @Override
        public Optional<Instant> lastConfirmedPickup(UUID storeId, UUID userId, Instant before) {
            return signups.stream().filter(s -> s.storeId().equals(storeId) && s.userId().equals(userId)
                    && s.confirmed() && s.pickupAt().isBefore(before)).map(Signup::pickupAt)
                    .max(Instant::compareTo);
        }

        @Override
        public List<Slot> slots(UUID storeId, Instant from, Instant to) {
            return storeId.equals(CLEANING) ? slots : List.of();
        }

        @Override
        public Optional<String> holidayName(UUID storeId, LocalDate date) {
            if (holidayDown) {
                throw new PickupPolicyEvaluator.HolidayCalendarUnavailableException("down", null);
            }
            return Optional.ofNullable(holidays.get(date));
        }

        @Override
        public boolean cleaningExempt(UUID regionId, UUID userId) {
            return exempt;
        }

        @Override
        public Optional<Spec> enabledPolicy(UUID storeId) {
            return Optional.ofNullable(policies.get(storeId));
        }

        @Override
        public Instant extendDeadline(Instant start, Instant deadline) {
            return deadline.plus(outage);
        }

        @Override
        public String storeLink(UUID storeId) {
            return "http://localhost:4200/stores/" + storeId;
        }

        @Override
        public int cleaningBackCheckMonths() {
            return 12;
        }

        @Override
        public int minimumFreeCleaningSlots() {
            return 4;
        }
    }
}
