package network.foodsharing.pickup.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.domain.RegularPickup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.pickup.repository.RegularPickupRepository;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of pickups (A20): a store's schedule in a range (regular occurrences in the store zone, one-time rows,
 * sign-ups beyond the horizon), the caller's eligibility per pickup, and the region pickup rule.
 */
@Service
@Transactional(readOnly = true)
public class PickupScheduleService {

    /** A pickup with the caller's eligibility (null when not computed, e.g. for past pickups). */
    public record PickupView(PickupSchedule.Slot slot, boolean available, PickupEligibilityRules.Result eligibility,
                             network.foodsharing.pickup.domain.PickupExtraSlot extraSlot, boolean mayAddExtraSlot) {

        public PickupView(PickupSchedule.Slot slot, boolean available, PickupEligibilityRules.Result eligibility) {
            this(slot, available, eligibility, null, false);
        }
    }

    private final RegularPickupRepository regular;
    private final PickupDateRepository dates;
    private final PickupSignupRepository signups;
    private final network.foodsharing.pickup.repository.PickupExtraSlotRepository extraSlots;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final RegionService regions;
    private final RegionOptionsService regionOptions;
    private final QuizSessionService quizzes;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final java.time.Clock clock;

    public PickupScheduleService(RegularPickupRepository regular, PickupDateRepository dates,
                                 PickupSignupRepository signups,
                                 network.foodsharing.pickup.repository.PickupExtraSlotRepository extraSlots,
                                 StoreService stores, StorePermissionService rights,
                                 RegionService regions, RegionOptionsService regionOptions,
                                 QuizSessionService quizzes, UserService users, CurrentActorService currentActor,
                                 java.time.Clock clock) {
        this.regular = regular;
        this.dates = dates;
        this.signups = signups;
        this.extraSlots = extraSlots;
        this.stores = stores;
        this.rights = rights;
        this.regions = regions;
        this.regionOptions = regionOptions;
        this.quizzes = quizzes;
        this.users = users;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    public ZoneId zoneOf(Store store) {
        return regions.zoneOf(store.getRegionId());
    }

    /** The store's pickups with {@code from <= pickupAt < to} (no permission check). */
    public List<PickupSchedule.Slot> schedule(Store store, Instant from, Instant to) {
        return schedules(List.of(store), from, to).getOrDefault(store.getId(), List.of());
    }

    /** Batch variant for store lists; one query per table. */
    public Map<UUID, List<PickupSchedule.Slot>> schedules(Collection<Store> stores, Instant from, Instant to) {
        if (stores.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = stores.stream().map(Store::getId).toList();
        Map<UUID, List<RegularPickup>> regularByStore = regular.findByStoreIdIn(ids).stream()
                .collect(Collectors.groupingBy(RegularPickup::getStoreId));
        Map<UUID, List<PickupDate>> datesByStore = dates.findInRange(ids, from, to).stream()
                .collect(Collectors.groupingBy(PickupDate::getStoreId));
        Map<UUID, List<PickupSignup>> signupsByStore = signups.findInRange(ids, from, to).stream()
                .collect(Collectors.groupingBy(PickupSignup::getStoreId));
        Map<UUID, List<PickupSchedule.Slot>> result = new HashMap<>();
        for (Store store : stores) {
            List<PickupSchedule.Slot> occurrences = PickupSchedule.regularOccurrences(store.getId(),
                    regularByStore.getOrDefault(store.getId(), List.of()), zoneOf(store), from, to);
            result.put(store.getId(), PickupSchedule.merge(store.getId(), occurrences,
                    datesByStore.getOrDefault(store.getId(), List.of()),
                    signupsByStore.getOrDefault(store.getId(), List.of())));
        }
        return result;
    }

    /** The pickup at exactly this time, if the schedule has it. */
    public Optional<PickupSchedule.Slot> find(Store store, Instant pickupAt) {
        return schedule(store, pickupAt, pickupAt.plusMillis(1)).stream()
                .filter(s -> s.pickupAt().equals(pickupAt)).findFirst();
    }

    /**
     * Pickups of a store; default range now .. now + prefetch days, plus sign-ups after the range (upstream
     * {@code getPickupSlots}). Future pickups carry the caller's eligibility.
     *
     * @throws ApiException 404, 403 unless maySeePickups, 400 VALIDATION_FAILED when from ≥ to
     */
    public List<PickupView> listPickups(UUID storeId, Instant from, Instant to) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.rights(actor, store).maySeePickups());
        Instant now = clock.instant();
        Instant start = from == null ? now : from;
        Instant end = to == null ? now.plus(Duration.ofDays(store.getPrefetchDays())) : to;
        if (!start.isBefore(end)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<PickupSchedule.Slot> slots = new ArrayList<>(schedule(store, start, end));
        if (to == null) {
            Map<Instant, List<PickupSignup>> later = signups.findInRange(List.of(storeId), end, Instant.MAX).stream()
                    .collect(Collectors.groupingBy(PickupSignup::getPickupAt));
            if (!later.isEmpty()) {
                List<PickupDate> laterDates = dates.findInRange(List.of(storeId), end, Instant.MAX).stream()
                        .filter(d -> later.containsKey(d.getPickupAt())).toList();
                List<PickupSchedule.Slot> laterRegular = new ArrayList<>();
                for (Instant at : later.keySet()) {
                    laterRegular.addAll(PickupSchedule.regularOccurrences(storeId, regular.findByStoreId(storeId),
                            zoneOf(store), at, at.plusMillis(1)));
                }
                slots.addAll(PickupSchedule.merge(storeId, laterRegular, laterDates,
                        later.values().stream().flatMap(List::stream).toList()));
            }
        }
        EligibilityContext context = context(store, actor.userId());
        Map<Instant, network.foodsharing.pickup.domain.PickupExtraSlot> extras = slots.isEmpty() ? Map.of()
                : extraSlots.findByStoreIdAndPickupAtIn(storeId, slots.stream().map(PickupSchedule.Slot::pickupAt)
                        .toList()).stream().collect(Collectors.toMap(e -> e.getPickupAt(), e -> e, (a, b) -> a));
        return slots.stream().map(s -> {
            var extra = extras.get(s.pickupAt());
            boolean mayAdd = store.isExtraSlotAllowed() && extra == null && s.pickupAt().isAfter(now)
                    && PickupSignupService.isFirstSignup(s, actor.userId());
            return new PickupView(s, s.isAvailable(now),
                    s.pickupAt().isBefore(now) ? null : context.evaluate(s.pickupAt(), s), extra, mayAdd);
        }).toList();
    }

    /** All failing reasons for the current user plus the rule counters. */
    public PickupEligibilityRules.Result eligibility(UUID storeId, Instant pickupAt) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(PickupEligibilityRules.mayCheckEligibility(
                rights.rights(actor, store).maySeePickups(), rights.teamStatus(actor.userId(), storeId)));
        return context(store, actor.userId()).evaluate(pickupAt, find(store, pickupAt).orElse(null));
    }

    /** Eligibility of {@code userId} for pickups of one store; loads the user's data and rule sign-ups once. */
    public EligibilityContext context(Store store, UUID userId) {
        User user = users.require(userId);
        PickupEligibilityRules.Person person = new PickupEligibilityRules.Person(userId,
                rights.teamStatus(userId, store.getId()), user.isVerified(), store.isRequiresHygiene(),
                quizzes.hygieneValidUntil(userId).orElse(null), store.isRequiresPhone(),
                notBlank(user.getMobilePhone()) || notBlank(user.getLandlinePhone()));
        RegionOptionsService.PickupRule rule = store.isUseRegionPickupRule()
                ? regionOptions.read(store.getRegionId()).pickupRule() : null;
        return new EligibilityContext(store, person, rule, zoneOf(store));
    }

    /** Evaluates the chain for several pickups of one store and user. */
    public final class EligibilityContext {
        private final Store store;
        private final PickupEligibilityRules.Person person;
        private final RegionOptionsService.PickupRule rule;
        private final ZoneId zone;
        private List<PickupSignup> ruleSignups;
        private Instant loadedFrom;
        private Instant loadedTo;

        EligibilityContext(Store store, PickupEligibilityRules.Person person, RegionOptionsService.PickupRule rule,
                           ZoneId zone) {
            this.store = store;
            this.person = person;
            this.rule = rule;
            this.zone = zone;
        }

        public PickupEligibilityRules.Result evaluate(Instant pickupAt, PickupSchedule.Slot slot) {
            Instant now = clock.instant();
            PickupEligibilityRules.RuleState state = null;
            if (rule != null) {
                state = PickupEligibilityRules.rule(rule, zone, pickupAt, now, ruleSignups(pickupAt));
            }
            return PickupEligibilityRules.combine(PickupEligibilityRules.personal(person, pickupAt),
                    PickupEligibilityRules.slot(person.userId(), pickupAt, now, store.getPrefetchDays(), slot), state);
        }

        private List<Instant> ruleSignups(Instant pickupAt) {
            if (!rule.active()) {
                return List.of();
            }
            Duration margin = Duration.ofDays(rule.timespanDays() + 2L);
            if (ruleSignups == null || pickupAt.isBefore(loadedFrom.plus(margin))
                    || pickupAt.isAfter(loadedTo.minus(margin))) {
                Instant now = clock.instant();
                Instant horizon = now.plus(Duration.ofDays(store.getPrefetchDays()));
                loadedFrom = (pickupAt.isBefore(now) ? pickupAt : now).minus(margin);
                loadedTo = (pickupAt.isAfter(horizon) ? pickupAt : horizon).plus(margin);
                List<UUID> ruleStores = stores.pickupRuleStoreIds(store.getRegionId());
                ruleSignups = ruleStores.isEmpty() ? List.of()
                        : signups.findOfUserInStores(person.userId(), ruleStores, loadedFrom, loadedTo);
            }
            return ruleSignups.stream()
                    .filter(s -> !(s.getStoreId().equals(store.getId()) && s.getPickupAt().equals(pickupAt)))
                    .map(PickupSignup::getPickupAt).toList();
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
