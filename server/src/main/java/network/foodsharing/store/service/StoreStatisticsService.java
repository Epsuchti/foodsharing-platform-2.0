package network.foodsharing.store.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import network.foodsharing.store.repository.PickupWeightCategoryRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store statistics (plan §13.3 S13): confirmed past sign-ups grouped by month in the store's time zone; kg = pickups ×
 * average kg of the store's current weight category; per-member counts only with mayEditStore. Default range: the
 * last 12 months including the current one.
 */
@Service
public class StoreStatisticsService {

    public record Month(YearMonth month, int pickups, double kg) {
    }

    public record MemberPickups(User user, int pickups) {
    }

    public record Statistics(int totalPickups, double totalKg, PickupWeightCategory weightCategory, List<Month> months,
                             List<MemberPickups> perMember) {
    }

    private final StoreService stores;
    private final StorePermissionService rights;
    private final StorePickupInfo pickupInfo;
    private final PickupWeightCategoryRepository weights;
    private final RegionService regions;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final Clock clock;

    public StoreStatisticsService(StoreService stores, StorePermissionService rights, StorePickupInfo pickupInfo,
                                  PickupWeightCategoryRepository weights, RegionService regions, UserService users,
                                  CurrentActorService currentActor, Clock clock) {
        this.stores = stores;
        this.rights = rights;
        this.pickupInfo = pickupInfo;
        this.weights = weights;
        this.regions = regions;
        this.users = users;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    /** @throws ApiException 404, 403 unless mayAccessStore, 400 VALIDATION_FAILED when from is after to */
    @Transactional(readOnly = true)
    public Statistics statistics(UUID storeId, LocalDate from, LocalDate to) {
        Store store = stores.require(storeId);
        StorePermissionService.Rights r = rights.rights(currentActor.requirePrincipal(), store);
        StorePermissionService.require(r.mayAccess());
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
                ? pickupInfo.confirmedSignups(storeId, fromInstant, toInstant) : List.of();
        PickupWeightCategory weight = store.getWeightCategory() == null ? null
                : weights.findByCode(store.getWeightCategory()).orElse(null);
        double averageKg = weight == null || weight.getAverageKg() == null ? 0 : weight.getAverageKg().doubleValue();
        List<Month> months = months(rows, zone, YearMonth.from(start), YearMonth.from(end), averageKg);
        List<MemberPickups> perMember = r.mayEditStore() ? perMember(rows) : null;
        return new Statistics(rows.size(), round(rows.size() * averageKg), weight, months, perMember);
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

    private List<MemberPickups> perMember(List<StorePickupInfo.PastSignup> rows) {
        Map<UUID, Long> counts = rows.stream().collect(Collectors.groupingBy(StorePickupInfo.PastSignup::userId,
                Collectors.counting()));
        Map<UUID, User> byId = users.findAll(counts.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return counts.entrySet().stream().filter(e -> byId.containsKey(e.getKey()))
                .map(e -> new MemberPickups(byId.get(e.getKey()), e.getValue().intValue()))
                .sorted(Comparator.comparingInt(MemberPickups::pickups).reversed()
                        .thenComparing(m -> m.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static double round(double kg) {
        return Math.round(kg * 10) / 10.0;
    }
}
