package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.service.IntroPickupBookingService;
import network.foodsharing.intropickup.service.IntroPickupViews;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pickups seen from a user or as history (PU6–PU8, B5): store pickup history, own upcoming / past pickups, sign-out
 * of all future pickups, pickup options across the user's stores, and the day agenda (pickups + EinAbs).
 */
@Service
public class PickupUserService {

    /** A pickup of a store as seen by one user ({@code confirmed}: that user's sign-up; null for options). */
    public record UserPickupView(Store store, ZoneId zone, PickupSchedule.Slot slot, Boolean confirmed) {
    }

    public record AgendaView(LocalDate date, List<UserPickupView> pickups,
                             List<IntroPickupViews.BookingView> introPickups) {
    }

    static final int MAX_HISTORY_DAYS = 366;

    private final PickupScheduleService schedule;
    private final PickupSignupRepository signups;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final PermissionService permissions;
    private final UserService users;
    private final RegionService regions;
    private final IntroPickupBookingService introPickups;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final Clock clock;

    public PickupUserService(PickupScheduleService schedule, PickupSignupRepository signups, StoreService stores,
                             StorePermissionService rights, PermissionService permissions, UserService users,
                             RegionService regions, IntroPickupBookingService introPickups,
                             CurrentActorService currentActor, HistoryService history, Clock clock) {
        this.schedule = schedule;
        this.signups = signups;
        this.stores = stores;
        this.rights = rights;
        this.permissions = permissions;
        this.users = users;
        this.regions = regions;
        this.introPickups = introPickups;
        this.currentActor = currentActor;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Past pickups of a store with their slots, newest first; default range = the last 30 days.
     *
     * @throws ApiException 404, 403 unless maySeePickupHistory, 400 VALIDATION_FAILED / RANGE_TOO_LARGE (> 366 days)
     */
    @Transactional(readOnly = true)
    public Page<PickupSchedule.Slot> storeHistory(UUID storeId, Instant from, Instant to, Integer page, Integer size) {
        Store store = stores.require(storeId);
        StorePermissionService.require(rights.rights(currentActor.requirePrincipal(), store).maySeePickupHistory());
        Instant now = clock.instant();
        Instant end = to == null || to.isAfter(now) ? now : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        if (!start.isBefore(end)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(MAX_HISTORY_DAYS)) > 0) {
            throw ApiException.badRequest(ApiErrorCode.RANGE_TOO_LARGE);
        }
        List<PickupSchedule.Slot> slots = schedule.schedule(store, start, end).stream()
                .filter(s -> !s.signups().isEmpty()).sorted(Comparator.comparing(PickupSchedule.Slot::pickupAt)
                        .reversed()).toList();
        return page(slots, page, size);
    }

    /** A pickup sign-up for the personal calendar feed (A11). */
    public record CalendarPickup(UUID storeId, String storeName, String address, Instant pickupAt, boolean confirmed) {
    }

    /** Sign-ups of {@code userId} from {@code from} on, for the calendar feed (the caller checked the feed token). */
    @Transactional(readOnly = true)
    public List<CalendarPickup> calendarPickups(UUID userId, Instant from) {
        return views(userId, signups.findByUserIdAndPickupAtGreaterThanEqualOrderByPickupAt(userId, from)).stream()
                .map(view -> new CalendarPickup(view.store().getId(), view.store().getName(),
                        java.util.stream.Stream.of(view.store().getStreet(), java.util.stream.Stream.of(
                                        view.store().getPostcode(), view.store().getCity())
                                        .filter(java.util.Objects::nonNull).collect(Collectors.joining(" ")))
                                .filter(part -> part != null && !part.isBlank()).collect(Collectors.joining(", ")),
                        view.slot().pickupAt(), Boolean.TRUE.equals(view.confirmed())))
                .toList();
    }

    /** Upcoming pickups of the current user. */
    @Transactional(readOnly = true)
    public List<UserPickupView> myPickups() {
        UUID userId = currentActor.requireUserId();
        return views(userId, signups.findByUserIdAndPickupAtGreaterThanEqualOrderByPickupAt(userId, clock.instant()));
    }

    /** @throws ApiException 404 USER_NOT_FOUND, 403 unless self or mayAdministrateUser */
    @Transactional(readOnly = true)
    public Page<UserPickupView> pastPickups(UUID userId, Integer page, Integer size) {
        requireSelfOrAdmin(userId);
        PageRequest request = PageRequest.of(page == null ? 0 : Math.max(0, page),
                size == null ? 20 : Math.min(Math.max(1, size), 100));
        Page<PickupSignup> rows = signups.findByUserIdAndPickupAtLessThanOrderByPickupAtDesc(userId, clock.instant(),
                request);
        return new PageImpl<>(views(userId, rows.getContent()), request, rows.getTotalElements());
    }

    /**
     * Signs the user out of every future pickup: per sign-up SIGN_OUT_SLOT (self) or REMOVED_FROM_SLOT, per store
     * ALL_FUTURE_PICKUPS_DELETED with the reason.
     *
     * @return number of removed sign-ups
     * @throws ApiException 404 USER_NOT_FOUND, 403 unless self or mayAdministrateUser
     */
    @Transactional
    public int deleteAllFuture(UUID userId, String reason) {
        SessionPrincipal actor = requireSelfOrAdmin(userId);
        boolean self = actor.userId().equals(userId);
        String text = reason == null || reason.isBlank() ? null : reason.strip();
        List<PickupSignup> future = signups.findByUserIdAndPickupAtGreaterThanEqualOrderByPickupAt(userId,
                clock.instant());
        Map<UUID, Store> byId = stores.storesById(future.stream().map(PickupSignup::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        for (PickupSignup signup : future) {
            Store store = byId.get(signup.getStoreId());
            history.record(history.event(self ? StoreLogAction.SIGN_OUT_SLOT : StoreLogAction.REMOVED_FROM_SLOT,
                    signup.getStoreId()).secondarySubject(userId).region(store == null ? null : store.getRegionId())
                    .metadata("pickupAt", signup.getPickupAt().toString()).reason(text).build());
        }
        signups.deleteAll(future);
        for (Store store : byId.values()) {
            history.record(history.event(StoreLogAction.ALL_FUTURE_PICKUPS_DELETED, store.getId())
                    .secondarySubject(userId).region(store.getRegionId()).reason(text).build());
        }
        return future.size();
    }

    /**
     * Upstream {@code getPickupOptions}: pickups within each store's prefetch horizon in all stores where the current
     * user is a regular member; full ones only when the user is in them. Sorted by time.
     *
     * @throws ApiException 403 NOT_VERIFIED unless a verified foodsaver
     */
    @Transactional(readOnly = true)
    public List<UserPickupView> options() {
        User user = currentActor.requireUser();
        if (!user.isVerified() || !PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)) {
            throw ApiException.forbidden(ApiErrorCode.NOT_VERIFIED);
        }
        Instant now = clock.instant();
        List<UserPickupView> result = new ArrayList<>();
        List<Store> mine = stores.teamStoresOf(user.getId());
        int maxPrefetch = mine.stream().mapToInt(Store::getPrefetchDays).max().orElse(0);
        Map<UUID, List<PickupSchedule.Slot>> all = schedule.schedules(mine, now,
                now.plus(Duration.ofDays(maxPrefetch)));
        for (Store store : mine) {
            Instant horizon = now.plus(Duration.ofDays(store.getPrefetchDays()));
            ZoneId zone = schedule.zoneOf(store);
            for (PickupSchedule.Slot slot : all.getOrDefault(store.getId(), List.of())) {
                if (slot.pickupAt().isAfter(horizon) || slot.slotCount() == 0) {
                    continue;
                }
                boolean in = slot.hasSignup(user.getId());
                if (slot.occupied() < slot.slotCount() || in) {
                    Boolean confirmed = in ? slot.signups().stream().filter(s -> s.getUserId().equals(user.getId()))
                            .findFirst().map(PickupSignup::isConfirmed).orElse(null) : null;
                    result.add(new UserPickupView(store, zone, slot, confirmed));
                }
            }
        }
        result.sort(Comparator.comparing((UserPickupView v) -> v.slot().pickupAt())
                .thenComparing(v -> v.store().getName()));
        return result;
    }

    /**
     * Pickups and booked EinAbs of one day in the user's home region zone (Europe/Zurich without home region).
     *
     * @throws ApiException 404 USER_NOT_FOUND, 403 unless self or mayAdministrateUser
     */
    @Transactional(readOnly = true)
    public AgendaView agenda(UUID userId, LocalDate date) {
        requireSelfOrAdmin(userId);
        User user = users.require(userId);
        ZoneId zone = user.getHomeRegionId() == null ? ZoneId.of("Europe/Zurich") : regions.zoneOf(user.getHomeRegionId());
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<UserPickupView> pickups = views(userId,
                signups.findByUserIdAndPickupAtGreaterThanEqualAndPickupAtLessThanOrderByPickupAt(userId, from, to));
        return new AgendaView(date, pickups, introPickups.bookingsOf(userId, from, to));
    }

    private SessionPrincipal requireSelfOrAdmin(UUID userId) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        users.require(userId);
        StorePermissionService.require(actor.userId().equals(userId) || permissions.mayAdministrateUser(actor, userId));
        return actor;
    }

    /** One view per sign-up of {@code userId}, with the whole pickup (all slots) attached. */
    private List<UserPickupView> views(UUID userId, List<PickupSignup> rows) {
        Map<UUID, Store> byId = stores.storesById(rows.stream().map(PickupSignup::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        Map<String, UserPickupView> result = new LinkedHashMap<>();
        for (PickupSignup row : rows) {
            Store store = byId.get(row.getStoreId());
            if (store == null) {
                continue;
            }
            PickupSchedule.Slot slot = schedule.find(store, row.getPickupAt()).orElseGet(() ->
                    new PickupSchedule.Slot(store.getId(), row.getPickupAt(), 1, null, false, List.of(row)));
            result.putIfAbsent(store.getId() + "@" + row.getPickupAt(),
                    new UserPickupView(store, schedule.zoneOf(store), slot, row.isConfirmed()));
        }
        return List.copyOf(result.values());
    }

    private static <T> Page<T> page(List<T> all, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int sz = size == null ? 20 : Math.min(Math.max(1, size), 100);
        int from = Math.min(all.size(), p * sz);
        return new PageImpl<>(all.subList(from, Math.min(all.size(), from + sz)), PageRequest.of(p, sz), all.size());
    }
}
