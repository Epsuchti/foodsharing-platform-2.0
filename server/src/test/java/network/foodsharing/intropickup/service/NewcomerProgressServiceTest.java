package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.service.NewcomerProgressService.Booking;
import network.foodsharing.intropickup.service.NewcomerProgressService.Funnel;
import network.foodsharing.intropickup.service.NewcomerProgressService.GapSeverity;
import network.foodsharing.intropickup.service.NewcomerProgressService.NextStep;
import network.foodsharing.intropickup.service.NewcomerProgressService.Progress;
import network.foodsharing.intropickup.service.NewcomerProgressService.Stage;
import network.foodsharing.region.service.RegionSettingsService.IntroPickupSettings;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** Stages, next steps, stalled detection, funnel steps, medians and gap analysis (reference port). */
class NewcomerProgressServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final Duration STALLED = Duration.ofDays(30);
    private static final IntroPickupSettings ZURICH = new IntroPickupSettings(3, true, true, true, Set.of("ONLINE"),
            null);
    private static final IntroPickupSettings NO_FAIRTEILER = new IntroPickupSettings(3, true, true, false, Set.of(),
            null);

    private static User user(boolean verified, int registeredDaysAgo) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setVerified(verified);
        user.setRegisteredAt(NOW.minus(Duration.ofDays(registeredDaysAgo)));
        return user;
    }

    private static Booking done(IntroPickupCategory category, int daysAgo, boolean visit) {
        Instant start = NOW.minus(Duration.ofDays(daysAgo));
        return new Booking(SlotStatus.DONE, category, start, visit, start.minus(Duration.ofDays(5)), start);
    }

    private static Booking upcoming(IntroPickupCategory category, int inDays, boolean visit) {
        Instant start = NOW.plus(Duration.ofDays(inDays));
        return new Booking(SlotStatus.BOOKED, category, start, visit, NOW.minus(Duration.ofDays(1)), NOW);
    }

    private static Progress progress(User user, List<Booking> bookings, IntroPickupSettings settings) {
        return NewcomerProgressService.progress(user, bookings, settings, NOW, STALLED);
    }

    @Test
    void stagesAndNextSteps() {
        Progress fresh = progress(user(false, 3), List.of(), ZURICH);
        assertThat(fresh.stage()).isEqualTo(Stage.REGISTERED);
        assertThat(fresh.nextStep()).isEqualTo(NextStep.BOOK_EINAB);
        assertThat(fresh.target()).isEqualTo(3);

        Progress first = progress(user(false, 3), List.of(upcoming(IntroPickupCategory.BAKERY, 2, false)), ZURICH);
        assertThat(first.stage()).isEqualTo(Stage.FIRST_BOOKED);
        assertThat(first.nextStep()).isEqualTo(NextStep.BOOK_EINAB);

        Progress two = progress(user(false, 30), List.of(done(IntroPickupCategory.BAKERY, 10, false),
                upcoming(IntroPickupCategory.MARKET, 2, false)), ZURICH);
        assertThat(two.stage()).isEqualTo(Stage.IN_PROGRESS);
        assertThat(two.nextStep()).isEqualTo(NextStep.BOOK_FAIRTEILER_EINAB);

        Progress attend = progress(user(false, 30), List.of(done(IntroPickupCategory.BAKERY, 10, false),
                upcoming(IntroPickupCategory.MARKET, 2, false), upcoming(IntroPickupCategory.TAKEOUT, 3, true)),
                ZURICH);
        assertThat(attend.nextStep()).isEqualTo(NextStep.ATTEND_BOOKED);
        assertThat(attend.fairteilerBooked()).isTrue();

        Progress complete = progress(user(false, 60), List.of(done(IntroPickupCategory.BAKERY, 40, false),
                done(IntroPickupCategory.MARKET, 20, false), done(IntroPickupCategory.TAKEOUT, 5, true)), ZURICH);
        assertThat(complete.stage()).isEqualTo(Stage.COMPLETE);
        assertThat(complete.nextStep()).isEqualTo(NextStep.WAIT_FOR_VERIFICATION);

        Progress missing = progress(user(false, 60), List.of(done(IntroPickupCategory.BAKERY, 40, false),
                done(IntroPickupCategory.MARKET, 20, false), done(IntroPickupCategory.TAKEOUT, 5, false)), ZURICH);
        assertThat(missing.stage()).isEqualTo(Stage.FAIRTEILER_MISSING);
        assertThat(progress(missing.user(), List.of(done(IntroPickupCategory.BAKERY, 40, false),
                done(IntroPickupCategory.MARKET, 20, false), done(IntroPickupCategory.TAKEOUT, 5, false)),
                NO_FAIRTEILER).stage()).isEqualTo(Stage.COMPLETE);

        Progress verified = progress(user(true, 300), List.of(), ZURICH);
        assertThat(verified.stage()).isEqualTo(Stage.VERIFIED);
        assertThat(verified.nextStep()).isEqualTo(NextStep.DONE);
    }

    @Test
    void ignoredCategoriesAndCancelledBookingsDoNotCount() {
        Booking cancelled = new Booking(SlotStatus.CANCELLED, IntroPickupCategory.BAKERY, NOW.minus(Duration.ofDays(5)),
                false, null, NOW.minus(Duration.ofDays(9)));
        Progress p = progress(user(false, 30), List.of(done(IntroPickupCategory.ONLINE, 5, true), cancelled), ZURICH);
        assertThat(p.counted()).isZero();
        assertThat(p.stage()).isEqualTo(Stage.REGISTERED);
        // the online Fairteiler visit still counts as a visit
        assertThat(p.fairteilerDone()).isTrue();
        assertThat(p.bookableCategories()).doesNotContain(IntroPickupCategory.ONLINE);
    }

    @Test
    void stalledWithoutUpcomingAndOldActivity() {
        assertThat(progress(user(false, 45), List.of(), ZURICH).stalled()).isTrue();
        assertThat(progress(user(false, 10), List.of(), ZURICH).stalled()).isFalse();
        assertThat(progress(user(false, 90), List.of(done(IntroPickupCategory.BAKERY, 40, false)), ZURICH).stalled())
                .isTrue();
        assertThat(progress(user(false, 90), List.of(done(IntroPickupCategory.BAKERY, 40, false),
                upcoming(IntroPickupCategory.MARKET, 3, false)), ZURICH).stalled()).isFalse();
    }

    @Test
    void funnelStepsMediansAndNames() {
        List<Progress> all = new ArrayList<>();
        all.add(progress(user(false, 2), List.of(), ZURICH));
        all.add(progress(user(false, 10), List.of(), ZURICH));
        all.add(progress(user(false, 40), List.of(done(IntroPickupCategory.BAKERY, 9, false)), ZURICH));
        all.add(progress(user(true, 400), List.of(done(IntroPickupCategory.BAKERY, 300, false)), ZURICH));
        Funnel funnel = NewcomerProgressService.funnel(all, List.of(), ZURICH, null, NOW, 30, true);
        assertThat(funnel.totalUsers()).isEqualTo(4);
        assertThat(funnel.inTrainingUsers()).isEqualTo(3);
        assertThat(funnel.steps()).extracting(NewcomerProgressService.FunnelStep::stage).containsExactly(
                Stage.REGISTERED, Stage.FIRST_BOOKED, Stage.IN_PROGRESS, Stage.IN_PROGRESS, Stage.COMPLETE,
                Stage.VERIFIED);
        var registered = funnel.steps().getFirst();
        assertThat(registered.userCount()).isEqualTo(2);
        assertThat(registered.reachedCount()).isEqualTo(4);
        assertThat(registered.medianDaysInStep()).isEqualTo(6);
        assertThat(registered.users()).hasSize(2);
        assertThat(funnel.steps().get(2).introPickupCount()).isEqualTo(1);
        assertThat(funnel.steps().get(2).userCount()).isEqualTo(1);
        assertThat(funnel.introPickupsStillNeeded()).isEqualTo(3 + 3 + 2);
        Funnel counts = NewcomerProgressService.funnel(all, List.of(), ZURICH, 30, NOW, 30, false);
        assertThat(counts.steps().getFirst().users()).isEmpty();
        assertThat(counts.days()).isEqualTo(30);
    }

    @Test
    void gapsBySeverityShortfallAndCategory() {
        List<Progress> inTraining = List.of(progress(user(false, 2), List.of(), ZURICH),
                progress(user(false, 2), List.of(), ZURICH),
                progress(user(false, 30), List.of(done(IntroPickupCategory.BAKERY, 9, false),
                        done(IntroPickupCategory.MARKET, 5, false)), ZURICH));
        List<IntroPickup> open = List.of(slot(IntroPickupCategory.BAKERY, false), slot(IntroPickupCategory.BAKERY, true),
                slot(IntroPickupCategory.BAKERY, false), slot(IntroPickupCategory.MARKET, false),
                slot(IntroPickupCategory.TAKEOUT, true));
        var gaps = NewcomerProgressService.gaps(Set.of("ONLINE"), inTraining, open, true);
        assertThat(gaps).noneMatch(g -> g.category() == IntroPickupCategory.ONLINE);
        assertThat(gaps.getFirst().severity()).isEqualTo(GapSeverity.MISSING);
        var bakery = gaps.stream().filter(g -> g.category() == IntroPickupCategory.BAKERY && !g.withFairteiler())
                .findFirst().orElseThrow();
        assertThat(bakery.students()).isEqualTo(2);
        assertThat(bakery.openSlots()).isEqualTo(3);
        assertThat(bakery.severity()).isEqualTo(GapSeverity.OK);
        var market = gaps.stream().filter(g -> g.category() == IntroPickupCategory.MARKET && !g.withFairteiler())
                .findFirst().orElseThrow();
        assertThat(market.severity()).isEqualTo(GapSeverity.LOW);
        var takeoutVisit = gaps.stream().filter(g -> g.category() == IntroPickupCategory.TAKEOUT && g.withFairteiler())
                .findFirst().orElseThrow();
        assertThat(takeoutVisit.students()).isEqualTo(3);
        assertThat(takeoutVisit.severity()).isEqualTo(GapSeverity.LOW);
        for (int i = 1; i < gaps.size(); i++) {
            assertThat(gaps.get(i - 1).severity().compareTo(gaps.get(i).severity())).isLessThanOrEqualTo(0);
        }
        assertThat(NewcomerProgressService.severity(2, 0)).isEqualTo(GapSeverity.MISSING);
    }

    private static IntroPickup slot(IntroPickupCategory category, boolean visit) {
        IntroPickup pickup = new IntroPickup();
        pickup.setCategory(category);
        pickup.setVisitFairteiler(visit);
        return pickup;
    }
}
