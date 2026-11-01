package network.foodsharing.store.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.PickupWeightCategory;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store statistics (plan §13.3 S13): confirmed past sign-ups grouped by month in the store's time zone; kg = pickups ×
 * average kg of the store's current weight category; per-member counts (paged, resume item 14) only with
 * mayEditStore. Default range: the last 12 months including the current one.
 */
@Service
public class StoreStatisticsService {

    public record Month(YearMonth month, int pickups, double kg) {
    }

    /** Status is MEMBER or JUMPER of the team today, FORMER otherwise; lastPickupAt is within the range. */
    public record MemberPickups(User user, int pickups, Instant lastPickupAt, TeamListQuery.Status status,
                                boolean manager) {
    }

    public record Statistics(int totalPickups, double totalKg, PickupWeightCategory weightCategory, List<Month> months) {
    }

    private record Range(ZoneId zone, LocalDate start, LocalDate end, List<StorePickupInfo.PastSignup> rows) {
    }

    private final StoreService stores;
    private final StorePermissionService rights;
    private final StorePickupInfo pickupInfo;
    private final PickupWeightCategoryRepository weights;
    private final RegionService regions;
    private final UserService users;
    private final StoreMembershipRepository memberships;
    private final CurrentActorService currentActor;
    private final Clock clock;

    public StoreStatisticsService(StoreService stores, StorePermissionService rights, StorePickupInfo pickupInfo,
                                  PickupWeightCategoryRepository weights, RegionService regions, UserService users,
                                  StoreMembershipRepository memberships, CurrentActorService currentActor,
                                  Clock clock) {
        this.stores = stores;
        this.rights = rights;
        this.pickupInfo = pickupInfo;
        this.weights = weights;
        this.regions = regions;
        this.users = users;
        this.memberships = memberships;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /** @throws ApiException 404, 403 unless mayAccessStore, 400 VALIDATION_FAILED when from is after to */
    @Transactional(readOnly = true)
    public Statistics statistics(UUID storeId, LocalDate from, LocalDate to) {
        Store store = stores.require(storeId);
        StorePermissionService.Rights r = rights.rights(currentActor.requirePrincipal(), store);
        StorePermissionService.require(r.mayAccess());
        Range range = range(store, from, to);
        PickupWeightCategory weight = store.getWeightCategory() == null ? null
                : weights.findByCode(store.getWeightCategory()).orElse(null);
        double averageKg = weight == null || weight.getAverageKg() == null ? 0 : weight.getAverageKg().doubleValue();
        List<Month> months = months(range.rows(), range.zone(), YearMonth.from(range.start()),
                YearMonth.from(range.end()), averageKg);
        return new Statistics(range.rows().size(), round(range.rows().size() * averageKg), weight, months);
    }

    /**
     * One page of the people who picked up in the range (members, jumpers and former members): status filter, name
     * search, sort (default PICKUP_COUNT); stable order (decision 138).
     *
     * @throws ApiException 404, 403 unless mayEditStore, 400 VALIDATION_FAILED when from is after to
     */
    @Transactional(readOnly = true)
    public Page<MemberPickups> memberPickups(UUID storeId, LocalDate from, LocalDate to, TeamListQuery query) {
        Store store = stores.require(storeId);
        StorePermissionService.Rights r = rights.rights(currentActor.requirePrincipal(), store);
        StorePermissionService.require(r.mayEditStore());
        Range range = range(store, from, to);
        Map<UUID, StoreMembership> team = new HashMap<>();
        for (StoreMembership m : memberships.findByStoreId(storeId)) {
            if (m.getStatus() == StoreMembershipStatus.MEMBER || m.getStatus() == StoreMembershipStatus.JUMPER) {
                team.put(m.getUserId(), m);
            }
        }
        List<MemberPickups> list = perMember(range.rows(), team).stream()
                .filter(m -> matchesStatus(m, query.status())).filter(m -> query.matches(m.user()))
                .sorted(order(query.sort() == null ? TeamListQuery.Sort.PICKUP_COUNT : query.sort())).toList();
        return query.page(list);
    }

    /** The range with its confirmed sign-ups: default the last 12 months up to today, never past now. */
    private Range range(Store store, LocalDate from, LocalDate to) {
        ZoneId zone = regions.zoneOf(store.getRegionId());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? YearMonth.from(end).minusMonths(11).atDay(1) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Instant fromInstant = start.atStartOfDay(zone).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
        if (toInstant.isAfter(clock.instant())) {
            toInstant = clock.instant();
        }
        List<StorePickupInfo.PastSignup> rows = fromInstant.isBefore(toInstant)
                ? pickupInfo.confirmedSignups(store.getId(), fromInstant, toInstant) : List.of();
        return new Range(zone, start, end, rows);
    }

    /** Every month of the range (also empty ones), pickups counted by the pickup's month in the zone. */
    static List<Month> months(Collection<StorePickupInfo.PastSignup> rows, ZoneId zone, YearMonth first,
                              YearMonth last, double averageKg) {
        Map<YearMonth, Integer> counts = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            counts.put(m, 0);
        }
        for (StorePickupInfo.PastSignup row : rows) {
            counts.computeIfPresent(YearMonth.from(row.pickupAt().atZone(zone)), (k, v) -> v + 1);
        }
        List<Month> result = new ArrayList<>();
        counts.forEach((month, n) -> result.add(new Month(month, n, round(n * averageKg))));
        return result;
    }

    private List<MemberPickups> perMember(List<StorePickupInfo.PastSignup> rows, Map<UUID, StoreMembership> team) {
        Map<UUID, List<StorePickupInfo.PastSignup>> byUser = rows.stream()
                .collect(Collectors.groupingBy(StorePickupInfo.PastSignup::userId));
        Map<UUID, User> byId = users.findAll(byUser.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberPickups> result = new ArrayList<>();
        byUser.forEach((userId, signups) -> {
            User user = byId.get(userId);
            if (user == null) {
                return;
            }
            Instant last = signups.stream().map(StorePickupInfo.PastSignup::pickupAt).max(Comparator.naturalOrder())
                    .orElse(null);
            StoreMembership m = team.get(userId);
            TeamListQuery.Status status = m == null ? TeamListQuery.Status.FORMER
                    : m.getStatus() == StoreMembershipStatus.JUMPER ? TeamListQuery.Status.JUMPER
                    : TeamListQuery.Status.MEMBER;
            result.add(new MemberPickups(user, signups.size(), last, status, m != null && m.isManager()));
        });
        return result;
    }

    private static boolean matchesStatus(MemberPickups m, TeamListQuery.Status status) {
        return switch (status) {
            case ALL -> true;
            case MANAGER -> m.manager();
            default -> m.status() == status;
        };
    }

    static Comparator<MemberPickups> order(TeamListQuery.Sort sort) {
        Comparator<MemberPickups> byName = TeamListQuery.byName(MemberPickups::user);
        return switch (sort) {
            case NAME -> Comparator.comparing((MemberPickups m) -> !m.manager()).thenComparing(byName);
            case PICKUP_COUNT -> Comparator.comparingInt((MemberPickups m) -> -m.pickups()).thenComparing(byName);
            case LAST_PICKUP -> Comparator.comparing(MemberPickups::lastPickupAt,
                    Comparator.nullsLast(Comparator.<Instant>reverseOrder())).thenComparing(byName);
        };
    }

    static double round(double kg) {
        return Math.round(kg * 10) / 10.0;
    }
}
