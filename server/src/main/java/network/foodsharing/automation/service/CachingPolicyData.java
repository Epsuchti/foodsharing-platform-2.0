package network.foodsharing.automation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.service.PickupPolicyEvaluator.CleaningSlot;
import network.foodsharing.automation.service.PickupPolicyEvaluator.RegionInfo;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Signup;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.automation.service.PickupPolicyEvaluator.UserInfo;

/**
 * Per-request cache over {@link PickupPolicyData} for evaluating many pickups of one store and person (decision 165
 * S2, {@code listPickups} hot path): every lookup is memoised, sign-up ranges are loaded once in a wide window and
 * filtered in memory, holidays are loaded per year, and the experience of co-signed people is primed in one query.
 * Not thread-safe; one instance per call.
 */
final class CachingPolicyData implements PickupPolicyEvaluator.Data {

    /** Extra range loaded around the first request (gap look-back and the widest link window). */
    private static final Duration MARGIN = Duration.ofDays(PickupPolicyService.MAXIMUM_WINDOW_DAYS + 4L);

    private final PickupPolicyData delegate;
    private final Instant now;
    private final Map<UUID, ZoneId> zones = new HashMap<>();
    private final Map<UUID, String> storeNames = new HashMap<>();
    private final Map<UUID, UUID> regionIds = new HashMap<>();
    private final Map<UUID, RegionInfo> regions = new HashMap<>();
    private final Map<UUID, UserInfo> users = new HashMap<>();
    private final Map<Object, Window> windows = new HashMap<>();
    private final Map<List<Object>, Object> memo = new HashMap<>();
    private final Map<UUID, Set<UUID>> experienced = new HashMap<>();
    private final Map<UUID, Optional<Spec>> policies = new HashMap<>();

    /** Sign-ups of one key loaded for {@code from <= pickupAt < to}. */
    private record Window(Instant from, Instant to, List<Signup> signups) {

        boolean covers(Instant f, Instant t) {
            return !f.isBefore(from) && !t.isAfter(to);
        }

        List<Signup> slice(Instant f, Instant t) {
            return signups.stream().filter(s -> !s.pickupAt().isBefore(f) && s.pickupAt().isBefore(t)).toList();
        }
    }

    /** Pickups the person books in the same request (decision 165 S3): counted as their unconfirmed sign-ups. */
    private final List<Signup> planned;

    CachingPolicyData(PickupPolicyData delegate) {
        this(delegate, List.of());
    }

    CachingPolicyData(PickupPolicyData delegate, List<Signup> planned) {
        this.delegate = delegate;
        this.now = delegate.now();
        this.planned = List.copyOf(planned);
    }

    /** This cache's sign-ups plus the planned ones in range that are not stored yet. */
    private List<Signup> withPlanned(List<Signup> stored, java.util.function.Predicate<Signup> matches, Instant from,
                                     Instant to) {
        if (planned.isEmpty()) {
            return stored;
        }
        List<Signup> result = new java.util.ArrayList<>(stored);
        for (Signup signup : planned) {
            boolean known = stored.stream().anyMatch(s -> s.storeId().equals(signup.storeId())
                    && s.userId().equals(signup.userId()) && s.pickupAt().equals(signup.pickupAt()));
            if (!known && matches.test(signup) && !signup.pickupAt().isBefore(from) && signup.pickupAt().isBefore(to)) {
                result.add(signup);
            }
        }
        return result;
    }

    /** Loads the store's sign-ups for these pickups once (co-pickup experience check). */
    void primeStoreSignups(UUID storeId, Collection<Instant> pickupAts) {
        Instant from = pickupAts.stream().min(Instant::compareTo).orElse(now);
        Instant to = pickupAts.stream().max(Instant::compareTo).orElse(now).plusMillis(1);
        signups(storeId, from, to);
    }

    /** Resolves the experience of these people in the store with one query. */
    void primeExperience(UUID storeId, Collection<UUID> userIds) {
        Set<UUID> known = experienced.computeIfAbsent(storeId, id -> new HashSet<>());
        Set<UUID> asked = new HashSet<>(userIds);
        known.addAll(delegate.usersWithConfirmedPickup(storeId, asked, now));
        memo.put(List.of("experienceAsked", storeId), asked);
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public ZoneId zone(UUID storeId) {
        return zones.computeIfAbsent(storeId, delegate::zone);
    }

    @Override
    public String storeName(UUID storeId) {
        return storeNames.computeIfAbsent(storeId, delegate::storeName);
    }

    @Override
    public UUID regionId(UUID storeId) {
        return regionIds.computeIfAbsent(storeId, delegate::regionId);
    }

    @Override
    public RegionInfo region(UUID regionId) {
        return regions.computeIfAbsent(regionId, delegate::region);
    }

    @Override
    public UserInfo user(UUID userId) {
        return users.computeIfAbsent(userId, delegate::user);
    }

    @Override
    public List<Signup> signups(UUID storeId, Instant from, Instant to) {
        return withPlanned(window(List.of("store", storeId), from, to,
                (f, t) -> delegate.signups(storeId, f, t), Duration.ZERO), s -> s.storeId().equals(storeId), from, to);
    }

    @Override
    public List<Signup> signupsOfUser(UUID userId, Collection<UUID> storeIds, Instant from, Instant to) {
        if (storeIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> stores = Set.copyOf(storeIds);
        return withPlanned(window(List.of("user", userId, stores), from, to,
                (f, t) -> delegate.signupsOfUser(userId, stores, f, t), MARGIN),
                s -> s.userId().equals(userId) && stores.contains(s.storeId()), from, to);
    }

    private interface Loader {
        List<Signup> load(Instant from, Instant to);
    }

    private List<Signup> window(Object key, Instant from, Instant to, Loader loader, Duration margin) {
        Window window = windows.get(key);
        if (window == null || !window.covers(from, to)) {
            Instant f;
            Instant t;
            if (window == null) {
                // the first load is widened so the other checks of the evaluation hit the same window
                f = from.minus(margin);
                t = margin.isZero() ? to : max(to, now.plus(PickupPolicyEvaluator.HORIZON).plus(margin));
            } else {
                f = min(from, window.from());
                t = max(to, window.to());
            }
            window = new Window(f, t, loader.load(f, t));
            windows.put(key, window);
        }
        return window.slice(from, to);
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    @Override
    public Optional<Instant> lastConfirmedPickup(UUID storeId, UUID userId, Instant before) {
        return cached(List.of("lastConfirmedPickup", storeId, userId, before),
                () -> delegate.lastConfirmedPickup(storeId, userId, before));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasConfirmedPickup(UUID storeId, UUID userId, Instant before) {
        Set<UUID> asked = (Set<UUID>) memo.get(List.of("experienceAsked", storeId));
        if (before.equals(now) && asked != null && asked.contains(userId)) {
            return experienced.getOrDefault(storeId, Set.of()).contains(userId);
        }
        return lastConfirmedPickup(storeId, userId, before).isPresent();
    }

    @Override
    public Optional<Instant> lastConfirmedCleaning(Collection<UUID> fspIds, UUID userId, Instant before) {
        return cached(List.of("lastConfirmedCleaning", Set.copyOf(fspIds), userId, before),
                () -> delegate.lastConfirmedCleaning(fspIds, userId, before));
    }

    @Override
    public List<Instant> cleaningSignupsOfUser(UUID userId, Collection<UUID> fspIds, Instant from, Instant to) {
        return cached(List.of("cleaningSignups", userId, Set.copyOf(fspIds), from, to),
                () -> delegate.cleaningSignupsOfUser(userId, fspIds, from, to));
    }

    @Override
    public List<CleaningSlot> freeCleaningSlots(Collection<UUID> fspIds, Instant from, Instant to) {
        return cached(List.of("freeCleaningSlots", Set.copyOf(fspIds), from, to),
                () -> delegate.freeCleaningSlots(fspIds, from, to));
    }

    @Override
    public Optional<String> holidayName(UUID storeId, LocalDate date) {
        HolidayCalendarService.Place place = cached(List.of("place", storeId), () -> delegate.holidayPlace(storeId));
        List<HolidayCalendarService.Holiday> year = cached(List.of("holidays", place, date.getYear()),
                () -> delegate.holidays(date.getYear(), place));
        return year.stream().filter(h -> h.date().equals(date)).map(HolidayCalendarService.Holiday::name).findFirst();
    }

    @Override
    public boolean cleaningExempt(UUID regionId, UUID userId) {
        return cached(List.of("cleaningExempt", regionId, userId), () -> delegate.cleaningExempt(regionId, userId));
    }

    @Override
    public Optional<Spec> enabledPolicy(UUID storeId) {
        return policies.computeIfAbsent(storeId, delegate::enabledPolicy);
    }

    @Override
    public Instant extendDeadline(Instant start, Instant deadline) {
        return delegate.extendDeadline(start, deadline);
    }

    @Override
    public Optional<Instant> linkPendingDeadline(UUID storeId, Instant pickupAt, UUID userId) {
        Map<Instant, Instant> deadlines = cached(List.of("linkPending", storeId, userId),
                () -> delegate.linkPendingDeadlines(storeId, userId));
        return Optional.ofNullable(deadlines.get(pickupAt));
    }

    @Override
    public String cleaningLink(UUID fspId) {
        return cached(List.of("cleaningLink", fspId), () -> delegate.cleaningLink(fspId));
    }

    @Override
    public int cleaningBackCheckMonths() {
        return delegate.cleaningBackCheckMonths();
    }

    @Override
    public int minimumFreeCleaningSlots() {
        return delegate.minimumFreeCleaningSlots();
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(List<Object> key, java.util.function.Supplier<T> loader) {
        if (memo.containsKey(key)) {
            return (T) memo.get(key);
        }
        T value = loader.get();
        memo.put(key, value);
        return value;
    }
}
