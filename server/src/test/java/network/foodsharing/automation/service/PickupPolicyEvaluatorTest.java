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
import network.foodsharing.automation.service.PickupPolicyEvaluator.CleaningSlot;
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
    static final UUID CLEANING_2 = UUID.randomUUID();
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
        return new Spec(UUID.randomUUID(), STORE, true, gap, minGap, cleaning, experience, holiday, excluded,
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

    // ---------------------------------------------------------------- gap with a shared store list (decision 151)

    static final UUID CHAIN = UUID.randomUUID();
    static final UUID CHAIN_2 = UUID.randomUUID();

    Spec listed(int minGap, UUID... others) {
        data.names.put(CHAIN, "Coop Stauffacher");
        data.names.put(CHAIN_2, "Coop Wiedikon");
        return grouped(true, minGap, minGap, others);
    }

    /** This store with its own gap (rule {@code ownOn}) and a list with the group gap (decision 152). */
    Spec grouped(boolean ownOn, int ownGap, int groupGap, UUID... others) {
        data.names.put(CHAIN, "Coop Stauffacher");
        data.names.put(CHAIN_2, "Coop Wiedikon");
        return new Spec(UUID.randomUUID(), STORE, true, ownOn, ownGap, false, false, false, Set.of(), List.of(),
                List.of(others), others.length == 0 ? 0 : groupGap);
    }

    /** The person's own pending sign-up for {@code pickup} here, made at {@code signedUpAt}. */
    void own(Instant pickup, Instant signedUpAt) {
        data.signups.add(new Signup(STORE, USER, pickup, false, signedUpAt));
    }

    @Test
    void mondayAndWednesdayAreOneDayApartSameAndNextDayZero() {
        assertThat(PickupPolicyEvaluator.calendarGapDays(at("2026-11-09T19:00"), at("2026-11-11T19:00"), ZURICH))
                .isEqualTo(1);
        assertThat(PickupPolicyEvaluator.calendarGapDays(at("2026-11-09T08:00"), at("2026-11-09T19:00"), ZURICH))
                .isZero();
        assertThat(PickupPolicyEvaluator.calendarGapDays(at("2026-11-09T19:00"), at("2026-11-10T08:00"), ZURICH))
                .isZero();
    }

    @Test
    void confirmedPickupInAListedStoreDeclinesWithTheGroupText() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-09T19:00"), true, NOW.plusSeconds(60)));
        Decision d = evaluator.evaluate(listed(2, CHAIN, CHAIN_2), pickup, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).isEqualTo("Du hast bereits am 09.11.2026 eine Abholung bei Coop "
                + "Stauffacher. Zwischen zwei Abholungen bei Store GmbH, Coop Stauffacher und Coop Wiedikon müssen "
                + "mindestens 2 Tage liegen; hier wären es nur 1 Tag.");
    }

    @Test
    void earlierPendingSignupInAListedStoreBlocksALaterOneDoesNot() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW.minusSeconds(600));
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-10T19:00"), false, NOW.minusSeconds(900)));
        assertThat(rules(evaluator.evaluate(listed(2, CHAIN), pickup, USER, data))).containsExactly(PolicyRule.GAP);
        data.signups.removeIf(s -> s.storeId().equals(CHAIN));
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-10T19:00"), false, NOW.minusSeconds(300)));
        assertThat(evaluator.evaluate(listed(2, CHAIN), pickup, USER, data).allowed()).isTrue();
    }

    @Test
    void equalSignupTimesBlockBothSides() {
        Instant signedUp = NOW.minusSeconds(600);
        Instant here = at("2026-11-11T19:00");
        Instant there = at("2026-11-10T19:00");
        own(here, signedUp);
        data.signups.add(new Signup(CHAIN, USER, there, false, signedUp));
        assertThat(rules(evaluator.evaluate(listed(2, CHAIN), here, USER, data))).containsExactly(PolicyRule.GAP);
        // seen from the other store of the list
        Spec chain = new Spec(UUID.randomUUID(), CHAIN, true, true, 2, false, false, false, Set.of(),
                List.of(), List.of(STORE), 2);
        assertThat(rules(evaluator.evaluate(chain, there, USER, data))).containsExactly(PolicyRule.GAP);
    }

    @Test
    void missingSignupTimeInAListedStoreBlocks() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW.minusSeconds(600));
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-12T19:00"), false, null));
        assertThat(rules(evaluator.evaluate(listed(2, CHAIN), pickup, USER, data))).containsExactly(PolicyRule.GAP);
    }

    @Test
    void sameInstantInAListedStoreCounts() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, pickup, true, NOW));
        Decision d = evaluator.evaluate(listed(1, CHAIN), pickup, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).contains("hier wären es nur 0 Tage");
    }

    @Test
    void ownStoreStillCountsPendingAndConfirmedButNotTheSameTime() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW.minusSeconds(600));
        // a later own sign-up still blocks (D3: no tie-break in this store)
        own(at("2026-11-12T19:00"), NOW);
        Decision d = evaluator.evaluate(listed(2, CHAIN), pickup, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).contains("am 12.11.2026 eine Abholung bei Store GmbH");
    }

    @Test
    void pastAndNearPickupsInListedStoresAreIgnored() {
        Instant pickup = at("2026-11-06T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-01T19:00"), true, NOW.minusSeconds(86_400 * 5)));
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-04T19:00"), true, NOW.minusSeconds(60)));
        assertThat(evaluator.evaluate(listed(3, CHAIN), pickup, USER, data).allowed()).isTrue();
    }

    @Test
    void aListedStoreWithoutAutomationBlocksWithItsPickups() {
        // CHAIN has no enabled policy (data.policies is empty): its pickups count all the same
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-13T19:00"), true, NOW));
        assertThat(data.enabledPolicy(CHAIN)).isEmpty();
        assertThat(rules(evaluator.evaluate(listed(2, CHAIN), pickup, USER, data))).containsExactly(PolicyRule.GAP);
    }

    @Test
    void theFirstBlockingPickupByDateGivesTheText() {
        Instant pickup = at("2026-11-12T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN_2, USER, at("2026-11-13T19:00"), true, NOW));
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-11T08:00"), true, NOW));
        Decision d = evaluator.evaluate(listed(2, CHAIN, CHAIN_2), pickup, USER, data);
        assertThat(d.reasons().getFirst().text()).contains("am 11.11.2026 eine Abholung bei Coop Stauffacher");
    }

    @Test
    void groupTextInEveryLanguage() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-10T19:00"), true, NOW));
        for (String language : List.of("de", "en", "fr", "it", "gws")) {
            data.locale = Locale.forLanguageTag(language);
            String text = evaluator.evaluate(listed(2, CHAIN), pickup, USER, data).reasons().getFirst().text();
            assertThat(text).as(language).contains("10.11.2026").contains("Coop Stauffacher").doesNotContain("{");
        }
        data.locale = Locale.forLanguageTag("en");
        assertThat(evaluator.evaluate(listed(2, CHAIN), pickup, USER, data).reasons().getFirst().text())
                .contains("Store GmbH and Coop Stauffacher");
    }

    // ---------------------------------------------------------------- own gap and group gap (decision 152)

    static final String OWN_TEXT = "Zwischen den Abholungen liegt nur";
    static final String GROUP_TEXT = "Zwischen zwei Abholungen bei";

    @Test
    void ownGapOnlyAppliesToThisStoreWhenTheGroupGapIsMet() {
        // own 3 / group 1: an own pickup 1 day away fails the own check only
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        own(at("2026-11-13T19:00"), NOW);
        Decision d = evaluator.evaluate(grouped(true, 3, 1, CHAIN), pickup, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).isEqualTo(
                "Zwischen den Abholungen liegt nur 1 Tag Abstand; erforderlich sind mindestens 3 Tage.");
        // a pickup in a listed store 1 day away meets the group gap of 1 and is not checked with the own gap
        data.signups.removeIf(s -> s.storeId().equals(STORE) && !s.pickupAt().equals(pickup));
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-13T19:00"), true, NOW));
        assertThat(evaluator.evaluate(grouped(true, 3, 1, CHAIN), pickup, USER, data).allowed()).isTrue();
    }

    @Test
    void groupGapOnlyChecksWithTheOwnRuleOff() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-10T19:00"), true, NOW));
        Decision other = evaluator.evaluate(grouped(false, 0, 2, CHAIN), pickup, USER, data);
        assertThat(rules(other)).containsExactly(PolicyRule.GAP);
        assertThat(other.reasons().getFirst().text()).contains("am 10.11.2026 eine Abholung bei Coop Stauffacher")
                .contains(GROUP_TEXT).doesNotContain(OWN_TEXT);
        // an own-store pickup is checked by the group gap as well
        data.signups.removeIf(s -> s.storeId().equals(CHAIN));
        own(at("2026-11-12T19:00"), NOW);
        Decision self = evaluator.evaluate(grouped(false, 0, 2, CHAIN), pickup, USER, data);
        assertThat(rules(self)).containsExactly(PolicyRule.GAP);
        assertThat(self.reasons().getFirst().text()).contains("am 12.11.2026 eine Abholung bei Store GmbH")
                .doesNotContain(OWN_TEXT);
    }

    @Test
    void groupGapLargerThanTheOwnGapRejectsWhatTheOwnGapAllows() {
        // own 1 / group 3: an own pickup 1 day away passes the own check, fails the group check
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        own(at("2026-11-13T19:00"), NOW);
        Decision d = evaluator.evaluate(grouped(true, 1, 3, CHAIN), pickup, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).startsWith("Du hast bereits am 13.11.2026").contains("mindestens 3 Tage")
                .doesNotContain(OWN_TEXT);
        // the same pickup without the list passes
        Spec alone = new Spec(UUID.randomUUID(), STORE, true, true, 1, false, false, false, Set.of(),
                List.of());
        assertThat(evaluator.evaluate(alone, pickup, USER, data).allowed()).isTrue();
    }

    @Test
    void bothFailWithOwnGapAtMostTheGroupGapGiveOnlyTheGroupSentence() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        own(at("2026-11-12T19:00"), NOW);
        Decision d = evaluator.evaluate(grouped(true, 2, 3, CHAIN), pickup, USER, data);
        assertThat(d.reasons()).hasSize(1);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).contains(GROUP_TEXT).doesNotContain(OWN_TEXT).doesNotContain("\n");
    }

    @Test
    void bothFailWithOwnGapAboveTheGroupGapGiveBothSentencesInOneReason() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        own(at("2026-11-12T19:00"), NOW);
        Decision d = evaluator.evaluate(grouped(true, 3, 1, CHAIN), pickup, USER, data);
        assertThat(d.reasons()).hasSize(1);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        String[] lines = d.reasons().getFirst().text().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).isEqualTo(
                "Zwischen den Abholungen liegt nur 0 Tage Abstand; erforderlich sind mindestens 3 Tage.");
        assertThat(lines[1]).startsWith("Du hast bereits am 12.11.2026").contains("mindestens 1 Tag liegen");
    }

    @Test
    void aGroupWithoutLiveMembersRunsTheOwnCheckOnly() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        own(at("2026-11-14T19:00"), NOW);
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-12T19:00"), true, NOW));
        // the group row says 5, but no other live store is left: only the own gap of 2 applies
        Spec lonely = new Spec(UUID.randomUUID(), STORE, true, true, 2, false, false, false, Set.of(),
                List.of(), List.of(), 5);
        assertThat(evaluator.evaluate(lonely, pickup, USER, data).allowed()).isTrue();
    }

    @Test
    void anEmptyListBehavesAsBeforeAndIgnoresOtherStores() {
        Instant pickup = at("2026-11-11T19:00");
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-10T19:00"), true, NOW));
        Spec alone = new Spec(UUID.randomUUID(), STORE, true, true, 2, false, false, false, Set.of(),
                List.of(), List.of(), 0);
        assertThat(evaluator.evaluate(alone, pickup, USER, data).allowed()).isTrue();
        data.signups.add(new Signup(STORE, USER, at("2026-11-12T19:00"), true, NOW));
        Decision d = evaluator.evaluate(alone, pickup, USER, data);
        assertThat(d.reasons().getFirst().text()).isEqualTo(
                "Zwischen den Abholungen liegt nur 0 Tage Abstand; erforderlich sind mindestens 2 Tage.");
    }

    @Test
    void calendarDaysOfTheListUseTheStoresZone() {
        // New York: the pickups are on the 11th and the 12th (0 days in between); in Zurich on the 11th and the 13th
        data.zone = ZoneId.of("America/New_York");
        Instant pickup = Instant.parse("2026-11-13T04:00:00Z");
        own(pickup, NOW);
        data.signups.add(new Signup(CHAIN, USER, Instant.parse("2026-11-11T20:00:00Z"), true, NOW));
        Decision d = evaluator.evaluate(listed(1, CHAIN), pickup, USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.GAP);
        assertThat(d.reasons().getFirst().text()).contains("am 11.11.2026").contains("nur 0 Tage");
        data.zone = ZURICH;
        assertThat(evaluator.evaluate(listed(1, CHAIN), pickup, USER, data).allowed()).isTrue();
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
            data.cleaningSlots.add(new CleaningSlot(at("2026-11-10T18:00").plus(Duration.ofDays(i)), CLEANING,
                    "Fairteiler Wiedikon"));
        }
    }

    @Test
    void noCleaningFairteilerDeclines() {
        data.cleaningFsps = List.of();
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.NO_CLEANING_FAIRTEILER);
        assertThat(d.reasons().getFirst().text()).contains("Zürich");
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

    /** Decision 155: the decline text lists at most three shifts, so names at their limit stay within mail and Telegram. */
    @Test
    void declineTextWithLongNamesStaysWithinTheChannelLimits() {
        String longName = "F".repeat(200);
        data.cleaningFsps = List.of(CLEANING, CLEANING_2);
        for (int i = 0; i < 20; i++) {
            data.cleaningSlots.add(new CleaningSlot(at("2026-11-10T18:00").plus(Duration.ofDays(i)), CLEANING, longName));
        }
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonText().length() + evaluator.texts(java.util.Locale.GERMAN).text("decline-footer").length() + 2)
                .isLessThanOrEqualTo(4000);
    }

    /** D5: an upcoming cleaning sign-up counts while it is still unconfirmed. */
    @Test
    void plannedCleaningPasses() {
        data.signups.add(new Signup(CLEANING, USER, at("2026-11-20T10:00"), false, NOW));
        assertThat(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data).allowed()).isTrue();
    }

    @Test
    void cleaningAtAnyOfTheRegionsFairteilerCounts() {
        data.cleaningFsps = List.of(CLEANING, CLEANING_2);
        data.signups.add(new Signup(CLEANING_2, USER, at("2026-05-01T10:00"), true, NOW));
        freeCleaningSlots(5);
        assertThat(rules(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data)))
                .containsExactly(PolicyRule.ALL_RULES_PASSED);
    }

    @Test
    void cleaningAtAFairteilerOutsideTheRegionListDoesNotCount() {
        data.signups.add(new Signup(CLEANING_2, USER, at("2026-05-01T10:00"), true, NOW));
        freeCleaningSlots(5);
        assertThat(rules(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data)))
                .containsExactly(PolicyRule.CLEANING_REQUIRED);
    }

    /** D6: the next three free shifts across all cleaning Fairteiler; the link goes to the earliest one's tab. */
    @Test
    void declineListsFreeShiftsAcrossFairteilerAndLinksTheEarliest() {
        data.cleaningFsps = List.of(CLEANING, CLEANING_2);
        data.cleaningSlots.add(new CleaningSlot(at("2026-11-09T18:00"), CLEANING_2, "Fairteiler Langstrasse"));
        freeCleaningSlots(4);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.CLEANING_REQUIRED);
        String text = d.reasons().getFirst().text();
        assertThat(text).contains("09.11.2026 18:00 – Fairteiler Langstrasse\n10.11.2026 18:00 – Fairteiler Wiedikon"
                + "\n11.11.2026 18:00 – Fairteiler Wiedikon").doesNotContain("12.11.2026 18:00")
                .contains("/food-share-points/" + CLEANING_2 + "/cleaning");
    }

    /** D6: without a free shift the link goes to the first configured Fairteiler. */
    @Test
    void withoutFreeShiftsTheLinkGoesToTheFirstFairteiler() {
        data.cleaningFsps = List.of(CLEANING_2, CLEANING);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(rules(d)).containsExactly(PolicyRule.CLEANING_REMINDER);
        assertThat(d.userMessage()).contains("/food-share-points/" + CLEANING_2 + "/cleaning");
    }

    @Test
    void fewFreeCleaningSlotsAllowWithReminder() {
        data.signups.add(new Signup(CLEANING, USER, at("2025-06-01T10:00"), true, NOW));
        freeCleaningSlots(3);
        Decision d = evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data);
        assertThat(d.allowed()).isTrue();
        assertThat(rules(d)).containsExactly(PolicyRule.CLEANING_REMINDER);
        assertThat(d.userMessage()).startsWith("Hey Sarah").contains("01.06.2025").contains("nur noch 3 freie")
                .contains("10.11.2026 18:00 – Fairteiler Wiedikon")
                .contains("/food-share-points/" + CLEANING + "/cleaning")
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
        Spec mirror = new Spec(UUID.randomUUID(), LINKED, true, false, 0, false, false, false, Set.of(),
                List.of(new LinkRule(List.of(STORE), LinkedStoreWindowMode.SAME_DAY, 0, 0, 20, null)));
        data.policies.put(LINKED, mirror);
        // the recursion stops at the evaluating store (guard), so both sides would be confirmed
        assertThat(evaluator.evaluate(linked(sameDay(20, null)), pickup, USER, data).allowed()).isTrue();
        // a linked policy that would decline keeps this one waiting
        data.policies.put(LINKED, new Spec(mirror.id(), LINKED, true, false, 0, false, true, false, Set.of(),
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

    // ---------------------------------------------------------------- params for the UI (decision 165 S2)

    static PickupPolicyEvaluator.Params params(Decision decision, PolicyRule rule) {
        return decision.reasons().stream().filter(r -> r.rule() == rule).findFirst().orElseThrow().params();
    }

    @Test
    void gapParamsNameTheClosePickupTheGapAndTheNextDayThatKeepsEveryGap() {
        data.signups.add(new Signup(STORE, USER, at("2026-11-10T19:00"), true, NOW.minusSeconds(3600)));
        data.signups.add(new Signup(STORE, USER, at("2026-11-15T19:00"), false, NOW.minusSeconds(60)));
        Decision d = evaluator.evaluate(policy(true, 2, false, false, false, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data);
        PickupPolicyEvaluator.Params p = params(d, PolicyRule.GAP);
        assertThat(p.lastPickupAt()).isEqualTo(at("2026-11-10T19:00"));
        assertThat(p.gapDays()).isEqualTo(2);
        assertThat(p.storeIds()).isEmpty();
        // 13.11. is far enough from 10.11. but too close to 15.11.; 18.11. keeps both
        assertThat(p.nextPossibleAt()).isEqualTo(at("2026-11-18T00:00"));
    }

    @Test
    void groupGapParamsNameTheOtherStoreOfTheList() {
        Instant pickup = at("2026-11-11T19:00");
        data.signups.add(new Signup(CHAIN, USER, at("2026-11-10T19:00"), true, NOW));
        Decision d = evaluator.evaluate(grouped(false, 0, 2, CHAIN), pickup, USER, data);
        PickupPolicyEvaluator.Params p = params(d, PolicyRule.GAP);
        assertThat(p.lastPickupAt()).isEqualTo(at("2026-11-10T19:00"));
        assertThat(p.storeIds()).containsExactly(CHAIN);
        assertThat(p.gapDays()).isEqualTo(2);
        assertThat(p.nextPossibleAt()).isEqualTo(at("2026-11-13T00:00"));
    }

    @Test
    void gapParamsFollowTheSentenceWhenBothChecksFail() {
        Instant pickup = at("2026-11-11T19:00");
        own(pickup, NOW);
        own(at("2026-11-12T19:00"), NOW);
        PickupPolicyEvaluator.Params ownWins = params(evaluator.evaluate(grouped(true, 3, 1, CHAIN), pickup, USER,
                data), PolicyRule.GAP);
        assertThat(ownWins.gapDays()).isEqualTo(3);
        assertThat(ownWins.lastPickupAt()).isEqualTo(at("2026-11-12T19:00"));
        // own 3 days after 12.11. → 16.11.; the group gap of 1 day is kept there too
        assertThat(ownWins.nextPossibleAt()).isEqualTo(at("2026-11-16T00:00"));
        PickupPolicyEvaluator.Params groupWins = params(evaluator.evaluate(grouped(true, 2, 3, CHAIN), pickup, USER,
                data), PolicyRule.GAP);
        assertThat(groupWins.gapDays()).isEqualTo(3);
    }

    @Test
    void linkedStoreParamsCarryTheStoresAndTheWindowDays() {
        Instant pickup = at("2026-11-12T19:00");
        data.signups.add(new Signup(STORE, USER, pickup, false, NOW.minus(Duration.ofHours(1))));
        LinkRule window = new LinkRule(List.of(LINKED), LinkedStoreWindowMode.LAST_DAYS, 1, 3, 20, null);
        PickupPolicyEvaluator.Params p = params(evaluator.evaluate(linked(window), pickup, USER, data),
                PolicyRule.LINKED_STORE);
        assertThat(p.storeIds()).containsExactly(LINKED);
        assertThat(p.windowMode()).isEqualTo(LinkedStoreWindowMode.LAST_DAYS);
        assertThat(p.before()).isEqualTo(1);
        assertThat(p.after()).isEqualTo(3);
        assertThat(p.from()).isEqualTo(at("2026-11-11T00:00"));
        assertThat(p.to()).isEqualTo(at("2026-11-15T00:00"));
    }

    @Test
    void cleaningParamsCarryTheMonthsAndTheFairteilerOfTheEarliestShift() {
        freeCleaningSlots(5);
        PickupPolicyEvaluator.Params never = params(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data),
                PolicyRule.CLEANING_REQUIRED);
        assertThat(never.monthsAgo()).isNull();
        assertThat(never.monthsRequired()).isEqualTo(12);
        assertThat(never.fspId()).isEqualTo(CLEANING);
        data.signups.add(new Signup(CLEANING, USER, at("2025-09-01T10:00"), true, NOW));
        assertThat(params(evaluator.evaluate(cleaning(), at("2026-11-12T19:00"), USER, data),
                PolicyRule.CLEANING_REQUIRED).monthsAgo()).isEqualTo(14);
    }

    @Test
    void experienceAndAllowingReasonsHaveNoParams() {
        Decision d = evaluator.evaluate(policy(false, 0, false, true, false, Set.of(), List.of()),
                at("2026-11-12T19:00"), USER, data);
        assertThat(params(d, PolicyRule.EXPERIENCE)).isEqualTo(PickupPolicyEvaluator.Params.NONE);
    }

    /** In-memory data. */
    static class FakeData implements PickupPolicyEvaluator.Data {

        final List<Signup> signups = new ArrayList<>();
        final List<CleaningSlot> cleaningSlots = new ArrayList<>();
        final Map<LocalDate, String> holidays = new HashMap<>();
        final Map<UUID, Spec> policies = new HashMap<>();
        final Map<UUID, String> names = new HashMap<>(Map.of(STORE, "Store GmbH", LINKED, "Linked AG", CLEANING,
                "Fairteiler"));
        boolean holidayDown;
        boolean exempt;
        ZoneId zone = ZURICH;
        Locale locale = Locale.GERMAN;
        List<UUID> cleaningFsps = List.of(CLEANING);
        Instant verifiedAt = Instant.parse("2020-01-01T00:00:00Z");
        Duration outage = Duration.ZERO;

        @Override
        public Instant now() {
            return NOW;
        }

        @Override
        public ZoneId zone(UUID storeId) {
            return zone;
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
            return new RegionInfo(REGION, "Zürich", cleaningFsps);
        }

        @Override
        public UserInfo user(UUID userId) {
            return new UserInfo(userId, "Sarah", verifiedAt, locale);
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
        public Optional<Instant> lastConfirmedCleaning(Collection<UUID> fspIds, UUID userId, Instant before) {
            return signups.stream().filter(s -> fspIds.contains(s.storeId()) && s.userId().equals(userId)
                    && s.confirmed() && s.pickupAt().isBefore(before)).map(Signup::pickupAt)
                    .max(Instant::compareTo);
        }

        @Override
        public List<Instant> cleaningSignupsOfUser(UUID userId, Collection<UUID> fspIds, Instant from, Instant to) {
            return signupsOfUser(userId, fspIds, from, to).stream().map(Signup::pickupAt).toList();
        }

        @Override
        public List<CleaningSlot> freeCleaningSlots(Collection<UUID> fspIds, Instant from, Instant to) {
            return cleaningSlots.stream().filter(s -> fspIds.contains(s.fspId()) && !s.at().isBefore(from)
                    && s.at().isBefore(to)).sorted(java.util.Comparator.comparing(CleaningSlot::at)).toList();
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
        public String cleaningLink(UUID fspId) {
            return "http://localhost:4200/food-share-points/" + fspId + "/cleaning";
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
