package network.foodsharing.intropickup.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.region.service.RegionSettingsService.IntroPickupSettings;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Newcomer progress towards verification and the newcomer funnel of a region (port of the reference
 * {@code NewcomerProgressService}). Target = the region's booking limit ({@code max(1, maxActiveBookingsPerUser)});
 * EinAbs in ignored categories do not count; a Fairteiler visit is required only when the region requires it.
 * Counting statuses are BOOKED and DONE: past ones count, future ones are upcoming; CANCELLED never counts.
 */
@Service
public class NewcomerProgressService {

    public enum Stage { REGISTERED, FIRST_BOOKED, IN_PROGRESS, FAIRTEILER_MISSING, COMPLETE, VERIFIED }

    public enum NextStep { BOOK_EINAB, BOOK_FAIRTEILER_EINAB, ATTEND_BOOKED, WAIT_FOR_VERIFICATION, DONE }

    /** Ordered by urgency (MISSING first). */
    public enum GapSeverity { MISSING, LOW, OK }

    public record Progress(User user, int target, int counted, int upcoming, boolean fairteilerRequired,
                           boolean fairteilerDone, boolean fairteilerBooked, Set<IntroPickupCategory> doneCategories,
                           Set<IntroPickupCategory> bookableCategories, Instant lastActivityAt, Stage stage,
                           NextStep nextStep, boolean stalled, Instant now) {

        public int remainingToBook() {
            return Math.max(0, target - counted - upcoming);
        }

        public boolean needsFairteilerBooking() {
            return fairteilerRequired && !fairteilerDone && !fairteilerBooked;
        }

        public boolean inTraining() {
            return stage != Stage.COMPLETE && stage != Stage.VERIFIED;
        }

        public int daysInStage() {
            return (int) Math.max(0, Duration.between(lastActivityAt, now).toDays());
        }
    }

    /** {@code introPickupCount} only for IN_PROGRESS steps; {@code users} empty unless names were requested. */
    public record FunnelStep(Stage stage, Integer introPickupCount, int userCount, int reachedCount, int stalledCount,
                             Integer medianDaysInStep, List<Progress> users) {
    }

    public record Gap(IntroPickupCategory category, boolean withFairteiler, int students, int openSlots,
                      GapSeverity severity) {
    }

    public record Funnel(int target, boolean fairteilerRequired, Integer days, int totalUsers, int inTrainingUsers,
                         int stalledUsers, int introPickupsStillNeeded, int openSlots,
                         int fairteilerVisitsStillNeeded, int openFairteilerSlots, int gapHorizonDays,
                         List<FunnelStep> steps, List<Gap> gaps) {
    }

    /** Booking data the computation needs (pure input, so the rules are unit-testable). */
    public record Booking(SlotStatus status, IntroPickupCategory category, Instant startAt, boolean visitFairteiler,
                          Instant bookedAt, Instant createdAt) {
    }

    private static final Set<SlotStatus> COUNTING = Set.of(SlotStatus.BOOKED, SlotStatus.DONE);

    private final RegionService regions;
    private final RegionSettingsService settings;
    private final RegionPermissionResolver resolver;
    private final MembershipService memberships;
    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupViews views;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final AppProperties properties;

    public NewcomerProgressService(RegionService regions, RegionSettingsService settings,
                                   RegionPermissionResolver resolver, MembershipService memberships,
                                   IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                                   IntroPickupViews views, UserService users, CurrentActorService currentActor,
                                   PermissionService permissions, AppProperties properties) {
        this.regions = regions;
        this.settings = settings;
        this.resolver = resolver;
        this.memberships = memberships;
        this.pickups = pickups;
        this.slots = slots;
        this.views = views;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.properties = properties;
    }

    /** Progress of a user in a region (no permission check; callers check). */
    @Transactional(readOnly = true)
    public Progress progressOf(UUID userId, UUID regionId) {
        Region region = regions.require(regionId);
        User user = users.require(userId);
        Map<UUID, IntroPickup> byId = new java.util.HashMap<>();
        List<IntroPickupSlot> booked = slots.findBookingsOfUserInRegion(userId, regionId);
        List<UUID> pickupIds = booked.stream().map(IntroPickupSlot::getIntroPickupId).distinct().toList();
        if (!pickupIds.isEmpty()) {
            pickups.findAllIn(pickupIds).forEach(p -> byId.put(p.getId(), p));
        }
        return progress(user, toBookings(booked, byId), settings.read(region).introPickups(), views.now(),
                stalledAfter());
    }

    /**
     * The funnel; guides see counts, CAN_MANAGE_USERS holders (and ORGA) also the names.
     *
     * @param days only users registered in the last {@code days} days (null = all)
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public Funnel funnel(UUID regionId, Integer days) {
        Region region = regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean withNames = permissions.has(actor, Permission.CAN_MANAGE_USERS, regionId);
        if (!withNames && !permissions.has(actor, Permission.CAN_GIVE_INTRO_PICKUPS, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        Instant now = views.now();
        IntroPickupSettings rules = settings.read(region).introPickups();
        Instant registeredAfter = days == null ? null : now.minus(Duration.ofDays(days));
        Set<UUID> guides = resolver.holders(Permission.CAN_GIVE_INTRO_PICKUPS, regionId);
        List<User> newcomers = users.findActive(memberships.membersOf(regionId)).stream()
                .filter(user -> !guides.contains(user.getId()))
                .filter(user -> registeredAfter == null || registered(user).isAfter(registeredAfter))
                .toList();
        List<IntroPickupSlot> booked = newcomers.isEmpty() ? List.of()
                : slots.findBookingsOfUsersInRegion(newcomers.stream().map(User::getId).toList(), regionId);
        List<UUID> pickupIds = booked.stream().map(IntroPickupSlot::getIntroPickupId).distinct().toList();
        Map<UUID, IntroPickup> byId = pickupIds.isEmpty() ? Map.of() : pickups.findAllIn(pickupIds).stream()
                .collect(Collectors.toMap(IntroPickup::getId, Function.identity()));
        Map<UUID, List<IntroPickupSlot>> byUser = booked.stream().filter(s -> COUNTING.contains(s.getStatus()))
                .collect(Collectors.groupingBy(IntroPickupSlot::getBookingUserId));
        List<Progress> progresses = newcomers.stream()
                .filter(user -> !user.isVerified() || byUser.containsKey(user.getId()))
                .map(user -> progress(user, toBookings(byUser.getOrDefault(user.getId(), List.of()), byId), rules, now,
                        stalledAfter()))
                .toList();
        Duration horizon = Duration.ofDays(properties.getIntropickup().getGapHorizonDays());
        List<IntroPickup> inHorizon = pickups.findBetween(regionId, now, now.plus(horizon));
        Map<UUID, IntroPickup> horizonById = inHorizon.stream()
                .collect(Collectors.toMap(IntroPickup::getId, Function.identity()));
        List<IntroPickup> openSlots = inHorizon.isEmpty() ? List.of()
                : slots.findByPickups(horizonById.keySet()).stream()
                .filter(s -> s.getStatus() == SlotStatus.AVAILABLE).map(s -> horizonById.get(s.getIntroPickupId()))
                .toList();
        return funnel(progresses, openSlots, rules, days, now, (int) horizon.toDays(), withNames);
    }

    // ------------------------------------------------------------------------------- pure computation

    static Funnel funnel(List<Progress> progresses, List<IntroPickup> allOpenSlots, IntroPickupSettings rules,
                         Integer days, Instant now, int horizonDays, boolean withNames) {
        int target = target(rules);
        Set<String> ignored = ignored(rules);
        List<IntroPickup> openSlots = allOpenSlots.stream().filter(p -> !ignored.contains(p.getCategory().name()))
                .toList();
        List<Progress> inTraining = progresses.stream().filter(Progress::inTraining).toList();
        return new Funnel(target, rules.requireFairteilerVisitByThirdIntroPickup(), days, progresses.size(),
                inTraining.size(), (int) progresses.stream().filter(Progress::stalled).count(),
                inTraining.stream().mapToInt(Progress::remainingToBook).sum(), openSlots.size(),
                (int) inTraining.stream().filter(Progress::needsFairteilerBooking).count(),
                (int) allOpenSlots.stream().filter(IntroPickup::isVisitFairteiler).count(), horizonDays,
                steps(progresses, target, rules.requireFairteilerVisitByThirdIntroPickup(), withNames, now),
                gaps(ignored, inTraining, openSlots, rules.requireFairteilerVisitByThirdIntroPickup()));
    }

    static Progress progress(User user, Collection<Booking> bookings, IntroPickupSettings rules, Instant now,
                             Duration stalledAfter) {
        Set<String> ignored = ignored(rules);
        List<Booking> relevant = bookings.stream().filter(b -> COUNTING.contains(b.status()))
                .filter(b -> !ignored.contains(b.category().name())).toList();
        List<Booking> counted = relevant.stream().filter(b -> b.startAt().isBefore(now)).toList();
        List<Booking> upcoming = relevant.stream().filter(b -> !b.startAt().isBefore(now)).toList();
        int target = target(rules);
        boolean fairteilerRequired = rules.requireFairteilerVisitByThirdIntroPickup();
        // As in the booking rule, a Fairteiler visit also counts when its EinAb is in an ignored category.
        List<Booking> visits = bookings.stream().filter(b -> COUNTING.contains(b.status()))
                .filter(Booking::visitFairteiler).toList();
        boolean fairteilerDone = visits.stream().anyMatch(b -> b.startAt().isBefore(now));
        boolean fairteilerBooked = !fairteilerDone && !visits.isEmpty();

        Set<IntroPickupCategory> done = EnumSet.noneOf(IntroPickupCategory.class);
        relevant.forEach(b -> done.add(b.category()));
        Set<IntroPickupCategory> bookable = EnumSet.allOf(IntroPickupCategory.class);
        bookable.removeIf(c -> ignored.contains(c.name()));
        if (rules.preventDuplicateCategoryBookings()) {
            bookable.removeAll(done);
        }
        Instant lastActivity = relevant.stream()
                .map(b -> b.startAt().isBefore(now) ? b.startAt() : b.bookedAt() == null ? b.createdAt() : b.bookedAt())
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(registered(user));

        Stage stage;
        if (user.isVerified()) {
            stage = Stage.VERIFIED;
        } else if (counted.size() >= target) {
            stage = fairteilerRequired && !fairteilerDone ? Stage.FAIRTEILER_MISSING : Stage.COMPLETE;
        } else if (!counted.isEmpty()) {
            stage = Stage.IN_PROGRESS;
        } else if (!upcoming.isEmpty()) {
            stage = Stage.FIRST_BOOKED;
        } else {
            stage = Stage.REGISTERED;
        }
        NextStep next;
        if (stage == Stage.VERIFIED) {
            next = NextStep.DONE;
        } else if (stage == Stage.COMPLETE) {
            next = NextStep.WAIT_FOR_VERIFICATION;
        } else if (fairteilerRequired && !fairteilerDone && !fairteilerBooked
                && counted.size() + upcoming.size() >= target - 1) {
            next = NextStep.BOOK_FAIRTEILER_EINAB;
        } else if (counted.size() + upcoming.size() < target) {
            next = NextStep.BOOK_EINAB;
        } else {
            next = NextStep.ATTEND_BOOKED;
        }
        boolean stalled = stage != Stage.VERIFIED && stage != Stage.COMPLETE && upcoming.isEmpty()
                && lastActivity.isBefore(now.minus(stalledAfter));
        return new Progress(user, target, counted.size(), upcoming.size(), fairteilerRequired, fairteilerDone,
                fairteilerBooked, done, bookable, lastActivity, stage, next, stalled, now);
    }

    private static List<FunnelStep> steps(List<Progress> progresses, int target, boolean fairteilerRequired,
                                          boolean withNames, Instant now) {
        Map<StepKey, List<Progress>> byStep = progresses.stream().collect(Collectors.groupingBy(
                NewcomerProgressService::stepKey));
        List<StepKey> keys = new ArrayList<>();
        keys.add(new StepKey(Stage.REGISTERED, null));
        keys.add(new StepKey(Stage.FIRST_BOOKED, null));
        for (int count = 1; count < target; count++) {
            keys.add(new StepKey(Stage.IN_PROGRESS, count));
        }
        // The booking rules normally prevent this state, so the step only shows when someone is in it.
        if (fairteilerRequired && byStep.containsKey(new StepKey(Stage.FAIRTEILER_MISSING, null))) {
            keys.add(new StepKey(Stage.FAIRTEILER_MISSING, null));
        }
        keys.add(new StepKey(Stage.COMPLETE, null));
        keys.add(new StepKey(Stage.VERIFIED, null));
        List<FunnelStep> steps = new ArrayList<>();
        int reached = progresses.size();
        for (StepKey key : keys) {
            List<Progress> inStep = byStep.getOrDefault(key, List.of()).stream()
                    .sorted(Comparator.comparing(Progress::lastActivityAt)).toList();
            steps.add(new FunnelStep(key.stage(), key.count(), inStep.size(), reached,
                    (int) inStep.stream().filter(Progress::stalled).count(), medianDays(inStep, now),
                    withNames ? inStep : List.of()));
            reached -= inStep.size();
        }
        return steps;
    }

    private static StepKey stepKey(Progress progress) {
        return progress.stage() == Stage.IN_PROGRESS
                ? new StepKey(Stage.IN_PROGRESS, Math.min(progress.counted(), progress.target() - 1))
                : new StepKey(progress.stage(), null);
    }

    static Integer medianDays(List<Progress> progresses, Instant now) {
        if (progresses.isEmpty()) {
            return null;
        }
        List<Long> days = progresses.stream()
                .map(p -> Math.max(0, Duration.between(p.lastActivityAt(), now).toDays())).sorted().toList();
        int middle = days.size() / 2;
        long median = days.size() % 2 == 1 ? days.get(middle) : (days.get(middle - 1) + days.get(middle)) / 2;
        return (int) median;
    }

    /**
     * Per non-ignored category: newcomers in training who still need EinAbs and may still book the category vs. open
     * places in the horizon; Fairteiler rows (except ONLINE) for newcomers who still need a Fairteiler visit.
     * Sorted by severity, then shortfall (descending), then category.
     */
    static List<Gap> gaps(Set<String> ignored, List<Progress> inTraining, List<IntroPickup> openSlots,
                          boolean fairteilerRequired) {
        Map<IntroPickupCategory, Integer> students = new EnumMap<>(IntroPickupCategory.class);
        Map<IntroPickupCategory, Integer> fairteilerStudents = new EnumMap<>(IntroPickupCategory.class);
        for (Progress progress : inTraining) {
            if (progress.remainingToBook() <= 0) {
                continue;
            }
            for (IntroPickupCategory category : progress.bookableCategories()) {
                students.merge(category, 1, Integer::sum);
                if (progress.needsFairteilerBooking() && category != IntroPickupCategory.ONLINE) {
                    fairteilerStudents.merge(category, 1, Integer::sum);
                }
            }
        }
        Map<IntroPickupCategory, Integer> supply = new EnumMap<>(IntroPickupCategory.class);
        Map<IntroPickupCategory, Integer> fairteilerSupply = new EnumMap<>(IntroPickupCategory.class);
        for (IntroPickup slot : openSlots) {
            supply.merge(slot.getCategory(), 1, Integer::sum);
            if (slot.isVisitFairteiler()) {
                fairteilerSupply.merge(slot.getCategory(), 1, Integer::sum);
            }
        }
        List<Gap> gaps = new ArrayList<>();
        for (IntroPickupCategory category : IntroPickupCategory.values()) {
            if (ignored.contains(category.name())) {
                continue;
            }
            int demand = students.getOrDefault(category, 0);
            if (demand > 0) {
                int open = supply.getOrDefault(category, 0);
                gaps.add(new Gap(category, false, demand, open, severity(demand, open)));
            }
            int fairteilerDemand = fairteilerStudents.getOrDefault(category, 0);
            if (fairteilerRequired && fairteilerDemand > 0) {
                int open = fairteilerSupply.getOrDefault(category, 0);
                gaps.add(new Gap(category, true, fairteilerDemand, open, severity(fairteilerDemand, open)));
            }
        }
        gaps.sort(Comparator.comparing(Gap::severity)
                .thenComparing((Gap gap) -> gap.students() - gap.openSlots(), Comparator.reverseOrder())
                .thenComparing(Gap::category));
        return gaps;
    }

    static GapSeverity severity(int students, int openSlots) {
        if (openSlots == 0) {
            return GapSeverity.MISSING;
        }
        return openSlots < students ? GapSeverity.LOW : GapSeverity.OK;
    }

    static int target(IntroPickupSettings rules) {
        return Math.max(1, rules.maxActiveBookingsPerUser());
    }

    private static Set<String> ignored(IntroPickupSettings rules) {
        return rules.ignoredCategories() == null ? Set.of() : rules.ignoredCategories();
    }

    private static Instant registered(User user) {
        return user.getRegisteredAt() != null ? user.getRegisteredAt() : user.getCreatedAt();
    }

    private Duration stalledAfter() {
        return Duration.ofDays(properties.getIntropickup().getStalledAfterDays());
    }

    private static List<Booking> toBookings(List<IntroPickupSlot> slots, Map<UUID, IntroPickup> byId) {
        return slots.stream().filter(s -> byId.containsKey(s.getIntroPickupId())).map(s -> {
            IntroPickup p = byId.get(s.getIntroPickupId());
            return new Booking(s.getStatus(), p.getCategory(), p.getStartAt(), p.isVisitFairteiler(), s.getBookedAt(),
                    s.getCreatedAt());
        }).toList();
    }

    private record StepKey(Stage stage, Integer count) {
    }
}
