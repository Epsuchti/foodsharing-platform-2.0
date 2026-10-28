package network.foodsharing.pickup.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupEligibilityReason;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.store.domain.UserTeamStatus;

/**
 * The sign-up eligibility chain (B4, upstream {@code mayDoPickup} + {@code isPickupSlotAvailable} +
 * {@code checkPickupRule}) as pure functions; reasons come out in the order of {@link PickupEligibilityReason}.
 */
public final class PickupEligibilityRules {

    /** Who wants to sign up, as far as the store's requirements are concerned. */
    public record Person(UUID userId, UserTeamStatus teamStatus, boolean verified, boolean requiresHygiene,
                         Instant hygieneValidUntil, boolean requiresPhone, boolean hasPhone) {
    }

    /** Region pickup rule state for one pickup (counters always filled when the rule applies). */
    public record RuleState(boolean active, int timespanDays, int limitNumber, int limitDayNumber, int inactiveHours,
                            int pickupsInTimespan, int pickupsSameDay, List<PickupEligibilityReason> reasons) {
    }

    public record Result(List<PickupEligibilityReason> reasons, RuleState rule) {
        public boolean eligible() {
            return reasons.isEmpty();
        }
    }

    private PickupEligibilityRules() {
    }

    /**
     * PU4 (upstream eligibility endpoint): who may ask for the eligibility result — everybody who may see the
     * pickups, plus every user with any relation to the team (jumper, applicant, invited, unverified member), who
     * then gets the reasons instead of a 403. Users without relation get 403.
     */
    public static boolean mayCheckEligibility(boolean maySeePickups, UserTeamStatus teamStatus) {
        return maySeePickups || teamStatus != null && teamStatus != UserTeamStatus.NO_MEMBER;
    }

    /** Team membership (jumpers and outsiders cannot pick up), verification, hygiene at pickup time, phone. */
    public static List<PickupEligibilityReason> personal(Person p, Instant pickupAt) {
        List<PickupEligibilityReason> reasons = new ArrayList<>();
        if (p.teamStatus() == UserTeamStatus.WAITING_LIST) {
            reasons.add(PickupEligibilityReason.JUMPER_CANNOT_PICKUP);
        } else if (p.teamStatus() != UserTeamStatus.MEMBER && p.teamStatus() != UserTeamStatus.COORDINATOR) {
            reasons.add(PickupEligibilityReason.NOT_TEAM_MEMBER);
        } else if (!p.verified()) {
            reasons.add(PickupEligibilityReason.NOT_VERIFIED);
        }
        if (p.requiresHygiene() && (p.hygieneValidUntil() == null || !p.hygieneValidUntil().isAfter(pickupAt))) {
            reasons.add(PickupEligibilityReason.HYGIENE_CERTIFICATE_REQUIRED);
        }
        if (p.requiresPhone() && !p.hasPhone()) {
            reasons.add(PickupEligibilityReason.PHONE_REQUIRED);
        }
        // SLEEPING is reserved for M6 (sleeping hats) and always passes.
        return reasons;
    }

    /** Time window (past, prefetch horizon) and the slot itself ({@code slot} null = no such pickup). */
    public static List<PickupEligibilityReason> slot(UUID userId, Instant pickupAt, Instant now, int prefetchDays,
                                                     PickupSchedule.Slot slot) {
        List<PickupEligibilityReason> reasons = new ArrayList<>();
        if (pickupAt.isBefore(now)) {
            reasons.add(PickupEligibilityReason.PICKUP_IN_PAST);
        }
        if (pickupAt.isAfter(now.plus(Duration.ofDays(prefetchDays)))) {
            reasons.add(PickupEligibilityReason.SIGNUP_NOT_YET_OPEN);
        }
        boolean signedUp = slot != null && slot.hasSignup(userId);
        if (slot == null || (slot.occupied() >= slot.slotCount() && !signedUp)) {
            reasons.add(PickupEligibilityReason.NO_FREE_SLOT);
        }
        if (signedUp) {
            reasons.add(PickupEligibilityReason.ALREADY_SIGNED_UP);
        }
        return reasons;
    }

    /**
     * Upstream {@code checkPickupRule}: only when the rule is active and more than {@code inactiveHours} (rounded up)
     * lie before the pickup. {@code others} are the user's other sign-ups (not this one) in stores of the region that use the rule;
     * they count when their date (region zone) is within ±timespanDays of the pickup's date, or on the same date.
     * Limits ≤ 0 mean "no limit".
     */
    public static RuleState rule(RegionOptionsService.PickupRule rule, ZoneId zone, Instant pickupAt, Instant now,
                                 Collection<Instant> others) {
        if (rule == null || !rule.active()) {
            return new RuleState(false, 0, 0, 0, 0, 0, 0, List.of());
        }
        LocalDate date = pickupAt.atZone(zone).toLocalDate();
        LocalDate first = date.minusDays(rule.timespanDays());
        LocalDate last = date.plusDays(rule.timespanDays());
        int inTimespan = 0;
        int sameDay = 0;
        for (Instant other : others) {
            LocalDate d = other.atZone(zone).toLocalDate();
            if (!d.isBefore(first) && !d.isAfter(last)) {
                inTimespan++;
            }
            if (d.equals(date)) {
                sameDay++;
            }
        }
        List<PickupEligibilityReason> reasons = new ArrayList<>();
        long minutes = Duration.between(now, pickupAt).toMinutes();
        long hoursUntil = Math.floorDiv(minutes + 59, 60);
        if (hoursUntil > rule.inactiveHours()) {
            if (rule.limitNumber() > 0 && inTimespan >= rule.limitNumber()) {
                reasons.add(PickupEligibilityReason.REGION_RULE_TIMESPAN_LIMIT);
            }
            if (rule.limitDayNumber() > 0 && sameDay >= rule.limitDayNumber()) {
                reasons.add(PickupEligibilityReason.REGION_RULE_DAY_LIMIT);
            }
        }
        return new RuleState(true, rule.timespanDays(), rule.limitNumber(), rule.limitDayNumber(),
                rule.inactiveHours(), inTimespan, sameDay, List.copyOf(reasons));
    }

    public static Result combine(List<PickupEligibilityReason> personal, List<PickupEligibilityReason> slot,
                                 RuleState rule) {
        List<PickupEligibilityReason> all = new ArrayList<>(personal);
        all.addAll(slot);
        if (rule != null) {
            all.addAll(rule.reasons());
        }
        return new Result(List.copyOf(all), rule);
    }
}
