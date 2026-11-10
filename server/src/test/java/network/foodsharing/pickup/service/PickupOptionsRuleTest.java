package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.store.domain.UserTeamStatus;
import org.junit.jupiter.api.Test;

/**
 * Review fix A1/A2: "Wo kann ich helfen?" and "Bald Hilfe gesucht" offer only pickups the sign-up would accept (the
 * whole eligibility chain plus the store rules) and keep the user's own sign-ups.
 */
class PickupOptionsRuleTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final UUID USER = UUID.randomUUID();
    static final Instant AT = Instant.parse("2026-11-06T18:00:00Z");
    /** Seed region Zürich: at most 2 within ±7 days, 1 per day, inactive 24 hours before the pickup. */
    static final RegionOptionsService.PickupRule RULE = new RegionOptionsService.PickupRule(true, 7, 2, 1, 24);

    static final PickupEligibilityRules.Person OK = new PickupEligibilityRules.Person(USER, UserTeamStatus.MEMBER,
            true, true, AT.plusSeconds(86_400), true, true);

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

    static PickupEligibilityRules.Result evaluate(PickupEligibilityRules.Person person, PickupSchedule.Slot slot,
                                                  List<Instant> otherSignups) {
        return PickupEligibilityRules.combine(PickupEligibilityRules.personal(person, AT),
                PickupEligibilityRules.slot(USER, AT, NOW, 14, slot),
                PickupEligibilityRules.rule(RULE, ZURICH, AT, NOW, otherSignups));
    }

    static boolean offered(PickupEligibilityRules.Person person, List<Instant> otherSignups) {
        PickupSchedule.Slot free = slot(2);
        return PickupUserService.isOffered(false, true, evaluate(person, free, otherSignups), false);
    }

    @Test
    void eligibleFreePickupIsOffered() {
        assertThat(offered(OK, List.of(Instant.parse("2026-11-04T17:30:00Z")))).isTrue();
    }

    @Test
    void regionRuleOverTheLimitHidesThePickup() {
        // two other sign-ups within ±7 days (different days than the pickup): the timespan limit of 2 is reached
        assertThat(offered(OK, List.of(Instant.parse("2026-11-04T17:30:00Z"),
                Instant.parse("2026-11-12T18:00:00Z")))).isFalse();
    }

    @Test
    void regionRuleDayLimitHidesThePickup() {
        assertThat(offered(OK, List.of(Instant.parse("2026-11-06T08:00:00Z")))).isFalse();
    }

    @Test
    void missingHygieneOrPhoneOrVerificationHidesThePickup() {
        var noHygiene = new PickupEligibilityRules.Person(USER, UserTeamStatus.MEMBER, true, true, null, true, true);
        var noPhone = new PickupEligibilityRules.Person(USER, UserTeamStatus.MEMBER, true, true,
                AT.plusSeconds(86_400), true, false);
        var unverified = new PickupEligibilityRules.Person(USER, UserTeamStatus.MEMBER, false, true,
                AT.plusSeconds(86_400), true, true);
        assertThat(offered(noHygiene, List.of())).isFalse();
        assertThat(offered(noPhone, List.of())).isFalse();
        assertThat(offered(unverified, List.of())).isFalse();
    }

    @Test
    void storeRuleBlockHidesAnOtherwiseEligiblePickup() {
        PickupSchedule.Slot free = slot(2);
        assertThat(PickupUserService.isOffered(false, true, evaluate(OK, free, List.of()), true)).isFalse();
    }

    @Test
    void fullOrUnevaluatedPickupIsNotOffered() {
        PickupSchedule.Slot full = slot(1, UUID.randomUUID());
        assertThat(PickupUserService.isOffered(false, false, evaluate(OK, full, List.of()), false)).isFalse();
        assertThat(PickupUserService.isOffered(false, true, null, false)).isFalse();
    }

    @Test
    void ownSignUpStaysEvenWhenTheChainOrAStoreRuleWouldRefuseIt() {
        PickupSchedule.Slot mine = slot(1, USER);
        var noHygiene = new PickupEligibilityRules.Person(USER, UserTeamStatus.MEMBER, true, true, null, true, true);
        PickupEligibilityRules.Result result = evaluate(noHygiene, mine, List.of(Instant.parse("2026-11-06T08:00:00Z")));
        assertThat(result.eligible()).isFalse();
        assertThat(PickupUserService.isOffered(true, false, result, true)).isTrue();
        assertThat(PickupUserService.isOffered(true, false, null, false)).isTrue();
    }
}
