package network.foodsharing.fsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.fsp.domain.CleaningEligibilityReason;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.service.FspCleaningRules;
import network.foodsharing.fsp.service.FspCleaningRules.Person;
import network.foodsharing.fsp.service.FspRules.Facts;
import org.junit.jupiter.api.Test;

/** FS2 cleaning rules: eligibility matrix (D1), managers pre-confirmed, sign-out, region candidates (D7). */
class FspCleaningRulesTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final Instant FUTURE = Instant.parse("2026-11-07T09:00:00Z");
    static final UUID USER = UUID.randomUUID();
    static final UUID REGION = UUID.randomUUID();

    static Person ok() {
        return new Person(USER, true, false);
    }

    static FspCleaningSignup signup(UUID userId) {
        FspCleaningSignup s = new FspCleaningSignup();
        s.setUserId(userId);
        s.setShiftAt(FUTURE);
        return s;
    }

    static SlotSchedule.Slot<FspCleaningSignup> slot(int count, UUID... users) {
        return new SlotSchedule.Slot<>(FUTURE, count, null, true,
                java.util.Arrays.stream(users).map(FspCleaningRulesTest::signup).toList());
    }

    static Facts facts(boolean mayAdd, boolean manager) {
        return new Facts(mayAdd, true, manager, manager, false, FspStatus.ACTIVE);
    }

    /** Decision 155. */
    @Test
    void autoConfirmSwitchConfirmsAtSignUpLikeAManager() {
        assertThat(FspCleaningRules.confirmedAtSignUp(facts(false, false), true)).isTrue();
        assertThat(FspCleaningRules.confirmedAtSignUp(facts(false, false), false)).isFalse();
        assertThat(FspCleaningRules.confirmedAtSignUp(facts(false, true), false)).isTrue();
    }

    @Test
    void verifiedAwakeFoodsaverWithAFreePlaceMaySignUp() {
        assertThat(FspCleaningRules.eligibility(ok(), FUTURE, NOW, slot(2, UUID.randomUUID()))).isEmpty();
    }

    @Test
    void everyFailingReasonIsListedInOrder() {
        Person sleepyUnverified = new Person(USER, false, true);
        assertThat(FspCleaningRules.eligibility(sleepyUnverified, NOW.minusSeconds(60), NOW, null)).containsExactly(
                CleaningEligibilityReason.NOT_VERIFIED, CleaningEligibilityReason.SLEEPING,
                CleaningEligibilityReason.SHIFT_IN_PAST, CleaningEligibilityReason.NO_FREE_SLOT);
    }

    @Test
    void fullShiftHasNoFreeSlot() {
        assertThat(FspCleaningRules.eligibility(ok(), FUTURE, NOW, slot(1, UUID.randomUUID())))
                .containsExactly(CleaningEligibilityReason.NO_FREE_SLOT);
    }

    @Test
    void missingOrCancelledShiftHasNoFreeSlot() {
        assertThat(FspCleaningRules.eligibility(ok(), FUTURE, NOW, null))
                .containsExactly(CleaningEligibilityReason.NO_FREE_SLOT);
        assertThat(FspCleaningRules.eligibility(ok(), FUTURE, NOW, slot(0)))
                .containsExactly(CleaningEligibilityReason.NO_FREE_SLOT);
    }

    @Test
    void ownSignupIsAlreadySignedUpEvenWhenFull() {
        assertThat(FspCleaningRules.eligibility(ok(), FUTURE, NOW, slot(1, USER)))
                .containsExactly(CleaningEligibilityReason.ALREADY_SIGNED_UP);
    }

    @Test
    void onlyUnverifiedOrOnlySleepingBlock() {
        assertThat(FspCleaningRules.eligibility(new Person(USER, false, false), FUTURE, NOW, slot(2)))
                .containsExactly(CleaningEligibilityReason.NOT_VERIFIED);
        assertThat(FspCleaningRules.eligibility(new Person(USER, true, true), FUTURE, NOW, slot(2)))
                .containsExactly(CleaningEligibilityReason.SLEEPING);
    }

    @Test
    void managersAndFspAdminsManageAndArePreConfirmed() {
        assertThat(FspCleaningRules.mayManage(facts(false, true))).isTrue();
        assertThat(FspCleaningRules.preConfirmed(facts(false, true))).isTrue();
        assertThat(FspCleaningRules.mayManage(facts(true, false))).isTrue();
        assertThat(FspCleaningRules.preConfirmed(facts(true, false))).isTrue();
        assertThat(FspCleaningRules.mayManage(facts(false, false))).isFalse();
        assertThat(FspCleaningRules.preConfirmed(facts(false, false))).isFalse();
    }

    @Test
    void selfOrManagersSignOut() {
        assertThat(FspCleaningRules.maySignOut(facts(false, false), true)).isTrue();
        assertThat(FspCleaningRules.maySignOut(facts(false, false), false)).isFalse();
        assertThat(FspCleaningRules.maySignOut(facts(false, true), false)).isTrue();
    }

    @Test
    void onlyActiveLiveFairteilerOfTheRegionWithCleaningAreCandidates() {
        assertThat(FspCleaningRules.isRegionCandidate(REGION, REGION, FspStatus.ACTIVE, false, true)).isTrue();
        assertThat(FspCleaningRules.isRegionCandidate(REGION, UUID.randomUUID(), FspStatus.ACTIVE, false, true))
                .as("other region").isFalse();
        assertThat(FspCleaningRules.isRegionCandidate(REGION, REGION, FspStatus.INACTIVE, false, true))
                .as("inactive").isFalse();
        assertThat(FspCleaningRules.isRegionCandidate(REGION, REGION, FspStatus.ACTIVE, true, true))
                .as("deleted").isFalse();
        assertThat(FspCleaningRules.isRegionCandidate(REGION, REGION, FspStatus.ACTIVE, false, false))
                .as("cleaning off").isFalse();
    }

    @Test
    void reasonsNeverRepeat() {
        List<CleaningEligibilityReason> reasons = FspCleaningRules.eligibility(new Person(USER, false, true),
                NOW.minusSeconds(1), NOW, slot(1, USER));
        assertThat(reasons).doesNotHaveDuplicates().contains(CleaningEligibilityReason.ALREADY_SIGNED_UP)
                .doesNotContain(CleaningEligibilityReason.NO_FREE_SLOT);
    }
}
