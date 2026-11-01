package network.foodsharing.fsp.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.CleaningEligibilityReason;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.domain.FspStatus;

/**
 * Cleaning shift rules (FS2, decision 148), pure so they are unit tested without a database. Any verified foodsaver
 * who isn't sleeping may sign up (D1); the people who may edit the Fairteiler ({@link FspRules#mayEdit}) manage the
 * shifts and sign up pre-confirmed.
 */
public final class FspCleaningRules {

    /** Who wants to sign up. {@code sleeping}: on the shift's date. */
    public record Person(UUID userId, boolean verified, boolean sleeping) {
    }

    private FspCleaningRules() {
    }

    /** All failing reasons in the order of {@link CleaningEligibilityReason}; {@code slot} null = no such shift. */
    public static List<CleaningEligibilityReason> eligibility(Person person, Instant shiftAt, Instant now,
                                                              SlotSchedule.Slot<FspCleaningSignup> slot) {
        List<CleaningEligibilityReason> reasons = new ArrayList<>();
        if (!person.verified()) {
            reasons.add(CleaningEligibilityReason.NOT_VERIFIED);
        }
        if (person.sleeping()) {
            reasons.add(CleaningEligibilityReason.SLEEPING);
        }
        if (shiftAt.isBefore(now)) {
            reasons.add(CleaningEligibilityReason.SHIFT_IN_PAST);
        }
        boolean signedUp = slot != null && hasSignup(slot, person.userId());
        if (slot == null || slot.occupied() >= slot.slotCount() && !signedUp) {
            reasons.add(CleaningEligibilityReason.NO_FREE_SLOT);
        }
        if (signedUp) {
            reasons.add(CleaningEligibilityReason.ALREADY_SIGNED_UP);
        }
        return List.copyOf(reasons);
    }

    public static boolean hasSignup(SlotSchedule.Slot<FspCleaningSignup> slot, UUID userId) {
        return slot.signups().stream().anyMatch(s -> s.getUserId().equals(userId));
    }

    /** Managers ({@link FspRules#mayEdit}): edit the schedule, confirm and remove sign-ups. */
    public static boolean mayManage(FspRules.Facts facts) {
        return FspRules.mayEdit(facts);
    }

    /** Managers sign up pre-confirmed; everybody else waits for a manager. */
    public static boolean preConfirmed(FspRules.Facts facts) {
        return mayManage(facts);
    }

    /** Self sign-out, or a manager removes someone. */
    public static boolean maySignOut(FspRules.Facts facts, boolean self) {
        return self || mayManage(facts);
    }

    /**
     * D7: a Fairteiler may count for the cleaning rule of a region when it lies in that region itself, is ACTIVE, not
     * deleted and has cleaning shifts.
     */
    public static boolean isRegionCandidate(UUID regionId, UUID fspRegionId, FspStatus status, boolean deleted,
                                            boolean cleaningEnabled) {
        return regionId.equals(fspRegionId) && status == FspStatus.ACTIVE && !deleted && cleaningEnabled;
    }
}
