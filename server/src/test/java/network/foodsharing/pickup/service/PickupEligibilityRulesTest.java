package network.foodsharing.pickup.service;

import static network.foodsharing.pickup.domain.PickupEligibilityReason.ALREADY_SIGNED_UP;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.HYGIENE_CERTIFICATE_REQUIRED;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.JUMPER_CANNOT_PICKUP;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.NOT_TEAM_MEMBER;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.NOT_VERIFIED;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.NO_FREE_SLOT;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.PHONE_REQUIRED;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.PICKUP_IN_PAST;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.REGION_RULE_DAY_LIMIT;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.REGION_RULE_TIMESPAN_LIMIT;
import static network.foodsharing.pickup.domain.PickupEligibilityReason.SIGNUP_NOT_YET_OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupEligibilityReason;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.store.domain.UserTeamStatus;
import org.junit.jupiter.api.Test;

/** The sign-up eligibility chain (B4): every reason, order, hygiene at pickup time, horizon edges, region rule. */
class PickupEligibilityRulesTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final UUID USER = UUID.randomUUID();
    static final Instant AT = Instant.parse("2026-11-05T18:00:00Z");

    static PickupEligibilityRules.Person person(UserTeamStatus status, boolean verified, Instant hygiene,
                                                boolean hasPhone) {
        return new PickupEligibilityRules.Person(USER, status, verified, true, hygiene, true, hasPhone);
    }

    static PickupSchedule.Slot slot(int slots, UUID... users) {
        List<PickupSignup> signups = new ArrayList<>();
        for (UUID u : users) {
            PickupSignup s = new PickupSignup();
            s.setUserId(u);
            s.setPickupAt(AT);
            signups.add(s);
        }
        return new PickupSchedule.Slot(UUID.randomUUID(), AT, slots, null, true, signups);
    }

    @Test
    void personalReasonsInOrder() {
        Instant valid = AT.plusSeconds(1);
        assertThat(PickupEligibilityRules.personal(person(UserTeamStatus.MEMBER, true, valid, true), AT)).isEmpty();
        assertThat(PickupEligibilityRules.personal(person(UserTeamStatus.COORDINATOR, true, valid, true), AT)).isEmpty();
        assertThat(PickupEligibilityRules.personal(person(UserTeamStatus.WAITING_LIST, true, valid, true), AT))
                .containsExactly(JUMPER_CANNOT_PICKUP);
        for (UserTeamStatus outsider : List.of(UserTeamStatus.NO_MEMBER, UserTeamStatus.APPLIED,
                UserTeamStatus.INVITED)) {
            assertThat(PickupEligibilityRules.personal(person(outsider, true, valid, true), AT))
                    .containsExactly(NOT_TEAM_MEMBER);
        }
        assertThat(PickupEligibilityRules.personal(person(UserTeamStatus.MEMBER, false, null, false), AT))
                .containsExactly(NOT_VERIFIED, HYGIENE_CERTIFICATE_REQUIRED, PHONE_REQUIRED);
    }

    @Test
    void hygieneMustBeValidAtPickupTime() {
        // valid today, expires before the pickup
        assertThat(PickupEligibilityRules.personal(person(UserTeamStatus.MEMBER, true, AT, true), AT))
                .containsExactly(HYGIENE_CERTIFICATE_REQUIRED);
        assertThat(PickupEligibilityRules.personal(person(UserTeamStatus.MEMBER, true, AT.minusSeconds(1), true), AT))
                .containsExactly(HYGIENE_CERTIFICATE_REQUIRED);
        var noRequirements = new PickupEligibilityRules.Person(USER, UserTeamStatus.MEMBER, true, false, null, false,
                false);
        assertThat(PickupEligibilityRules.personal(noRequirements, AT)).isEmpty();
    }

    @Test
    void slotReasons() {
        assertThat(PickupEligibilityRules.slot(USER, AT, NOW, 14, slot(2))).isEmpty();
        assertThat(PickupEligibilityRules.slot(USER, AT, NOW, 14, null)).containsExactly(NO_FREE_SLOT);
        assertThat(PickupEligibilityRules.slot(USER, AT, NOW, 14, slot(1, UUID.randomUUID())))
                .containsExactly(NO_FREE_SLOT);
        assertThat(PickupEligibilityRules.slot(USER, AT, NOW, 14, slot(1, USER))).containsExactly(ALREADY_SIGNED_UP);
        assertThat(PickupEligibilityRules.slot(USER, AT, AT.plusSeconds(60), 14, slot(2)))
                .containsExactly(PICKUP_IN_PAST);
        assertThat(PickupEligibilityRules.slot(USER, AT, AT, 14, slot(2))).isEmpty();
    }

    @Test
    void prefetchHorizonEdges() {
        Instant horizon = NOW.plus(Duration.ofDays(3));
        assertThat(PickupEligibilityRules.slot(USER, horizon, NOW, 3, slot(2))).isEmpty();
        assertThat(PickupEligibilityRules.slot(USER, horizon.minusSeconds(60), NOW, 3, slot(2))).isEmpty();
        assertThat(PickupEligibilityRules.slot(USER, horizon.plusSeconds(60), NOW, 3, slot(2)))
                .containsExactly(SIGNUP_NOT_YET_OPEN);
    }

    static final RegionOptionsService.PickupRule RULE = new RegionOptionsService.PickupRule(true, 7, 2, 1, 24);

    @Test
    void ruleCountsByDateWithinTimespan() {
        // AT = Thursday 2026-11-05 19:00 Zürich; window 2026-10-29 .. 2026-11-12 (dates in Zürich)
        List<Instant> two = List.of(Instant.parse("2026-10-28T23:30:00Z"), // 2026-10-29 00:30 local → inside
                Instant.parse("2026-11-12T18:00:00Z"));
        var state = PickupEligibilityRules.rule(RULE, ZURICH, AT, NOW, two);
        assertThat(state.pickupsInTimespan()).isEqualTo(2);
        assertThat(state.reasons()).containsExactly(REGION_RULE_TIMESPAN_LIMIT);
        List<Instant> outside = List.of(Instant.parse("2026-10-28T22:30:00Z"), // 2026-10-28 23:30 local → outside
                Instant.parse("2026-11-12T23:30:00Z")); // 2026-11-13 00:30 local → outside
        assertThat(PickupEligibilityRules.rule(RULE, ZURICH, AT, NOW, outside).reasons()).isEmpty();
    }

    @Test
    void ruleDayLimit() {
        var state = PickupEligibilityRules.rule(RULE, ZURICH, AT, NOW, List.of(Instant.parse("2026-11-05T08:00:00Z")));
        assertThat(state.pickupsSameDay()).isEqualTo(1);
        assertThat(state.reasons()).containsExactly(REGION_RULE_DAY_LIMIT);
        var both = PickupEligibilityRules.rule(RULE, ZURICH, AT, NOW, List.of(Instant.parse("2026-11-05T08:00:00Z"),
                Instant.parse("2026-11-04T08:00:00Z")));
        assertThat(both.reasons()).containsExactly(REGION_RULE_TIMESPAN_LIMIT, REGION_RULE_DAY_LIMIT);
    }

    @Test
    void ruleInactiveHoursRoundUp() {
        List<Instant> full = List.of(Instant.parse("2026-11-05T08:00:00Z"), Instant.parse("2026-11-04T08:00:00Z"));
        // exactly 24 h before: ceil(24) = 24, not > 24 → rule not applied
        assertThat(PickupEligibilityRules.rule(RULE, ZURICH, AT, AT.minus(Duration.ofHours(24)), full).reasons())
                .isEmpty();
        // 24 h 1 min before: ceil = 25 > 24 → applied
        assertThat(PickupEligibilityRules.rule(RULE, ZURICH, AT, AT.minus(Duration.ofMinutes(24 * 60 + 1)), full)
                .reasons()).isNotEmpty();
        // 23 h 1 min before: ceil = 24 → not applied
        assertThat(PickupEligibilityRules.rule(RULE, ZURICH, AT, AT.minus(Duration.ofMinutes(23 * 60 + 1)), full)
                .reasons()).isEmpty();
    }

    @Test
    void inactiveRuleAndZeroLimits() {
        var off = new RegionOptionsService.PickupRule(false, 7, 2, 1, 24);
        assertThat(PickupEligibilityRules.rule(off, ZURICH, AT, NOW, List.of(AT, AT)).active()).isFalse();
        assertThat(PickupEligibilityRules.rule(null, ZURICH, AT, NOW, List.of()).reasons()).isEmpty();
        var unlimited = new RegionOptionsService.PickupRule(true, 7, 0, 0, 0);
        assertThat(PickupEligibilityRules.rule(unlimited, ZURICH, AT, NOW,
                List.of(Instant.parse("2026-11-05T08:00:00Z"))).reasons()).isEmpty();
    }

    @Test
    void combinedOrderFollowsTheEnum() {
        var result = PickupEligibilityRules.combine(
                PickupEligibilityRules.personal(person(UserTeamStatus.WAITING_LIST, true, null, false), AT),
                PickupEligibilityRules.slot(USER, AT, NOW, 1, null),
                PickupEligibilityRules.rule(RULE, ZURICH, AT, NOW, List.of(Instant.parse("2026-11-05T08:00:00Z"))));
        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).isSortedAccordingTo(Enum::compareTo);
        assertThat(result.reasons()).contains(JUMPER_CANNOT_PICKUP, HYGIENE_CERTIFICATE_REQUIRED, PHONE_REQUIRED,
                SIGNUP_NOT_YET_OPEN, NO_FREE_SLOT, REGION_RULE_DAY_LIMIT);
        assertThat(result.reasons().getFirst()).isEqualTo(PickupEligibilityReason.JUMPER_CANNOT_PICKUP);
    }
}
