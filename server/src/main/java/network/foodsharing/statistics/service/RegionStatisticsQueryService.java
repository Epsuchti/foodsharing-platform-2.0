package network.foodsharing.statistics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import network.foodsharing.user.domain.Gender;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region statistics tab (R3/R13): gender counts, upstream age bands ({@code ageBandDistrict} / {@code
 * ageBandHomeDistrict}) and monthly pickups / kg of the region subtree. Members of the region or its administrators.
 */
@Service
public class RegionStatisticsQueryService {

    /** Upstream bands; "<18" and "invalid" (no or implausible date of birth) come last. */
    public static final List<String> AGE_BANDS =
            List.of("18-25", "26-33", "34-41", "42-49", "50-57", "58-65", "66+", "<18", "invalid");
    static final int MAX_MONTHS = 36;

    public record Count(String label, int count) {
    }

    public record Month(String month, int pickups, double kg) {
    }

    private final RegionService regions;
    private final UserService users;
    private final StatisticsReadModel readModel;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final Clock clock;

    public RegionStatisticsQueryService(RegionService regions, UserService users, StatisticsReadModel readModel,
                                        CurrentActorService currentActor, PermissionService permissions,
                                        Clock clock) {
        this.regions = regions;
        this.users = users;
        this.readModel = readModel;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.clock = clock;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public List<Count> genders(UUID regionId, boolean onlyHomeRegion) {
        return genderCounts(population(regionId, onlyHomeRegion));
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public List<Count> ageBands(UUID regionId, boolean onlyHomeRegion) {
        List<User> members = population(regionId, onlyHomeRegion);
        return ageBands(members, clock.instant().atZone(regions.zoneOf(regionId)).toLocalDate());
    }

    /**
     * The last {@code months} complete calendar months plus the current one, oldest first.
     *
     * @throws ApiException 400 VALIDATION_FAILED (months outside 1–36), 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public List<Month> monthlyPickups(UUID regionId, int months) {
        if (months < 1 || months > MAX_MONTHS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        requireViewer(regionId);
        ZoneId zone = regions.zoneOf(regionId);
        Instant now = clock.instant();
        YearMonth current = YearMonth.from(now.atZone(zone));
        YearMonth first = current.minusMonths(months - 1L);
        Instant from = first.atDay(1).atStartOfDay(zone).toInstant();
        return monthly(readModel.pickupsInRegions(regions.subtreeIds(regionId), from, now), first, current, zone);
    }

    public static List<Month> monthly(Collection<StatisticsReadModel.PickupRow> rows, YearMonth first, YearMonth last,
                               ZoneId zone) {
        Map<YearMonth, double[]> buckets = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            buckets.put(m, new double[2]);
        }
        for (StatisticsReadModel.PickupRow row : rows) {
            double[] bucket = buckets.get(YearMonth.from(row.pickupAt().atZone(zone)));
            if (bucket != null) {
                bucket[0]++;
                bucket[1] += row.kg();
            }
        }
        List<Month> result = new ArrayList<>();
        buckets.forEach((month, b) -> result.add(new Month(month.toString(), (int) b[0], Math.round(b[1] * 10) / 10.0)));
        return result;
    }

    public static List<Count> genderCounts(List<User> members) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Gender gender : Gender.values()) {
            counts.put(gender.name(), 0);
        }
        members.forEach(user -> counts.merge((user.getGender() == null ? Gender.NOT_SELECTED : user.getGender()).name(),
                1, Integer::sum));
        return counts.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    public static List<Count> ageBands(List<User> members, LocalDate today) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        AGE_BANDS.forEach(label -> counts.put(label, 0));
        members.forEach(user -> counts.merge(band(user.getDateOfBirth(), today), 1, Integer::sum));
        return counts.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    public static String band(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null || dateOfBirth.isAfter(today)) {
            return "invalid";
        }
        int age = Period.between(dateOfBirth, today).getYears();
        if (age > 120) {
            return "invalid";
        }
        if (age < 18) {
            return "<18";
        }
        if (age >= 66) {
            return "66+";
        }
        int lower = 18 + ((age - 18) / 8) * 8;
        return lower + "-" + (lower + 7);
    }

    private List<User> population(UUID regionId, boolean onlyHomeRegion) {
        requireViewer(regionId);
        return onlyHomeRegion ? users.findActiveByHomeRegions(regions.subtreeIds(regionId))
                : users.findActive(regions.activeMemberIds(regionId));
    }

    private void requireViewer(UUID regionId) {
        regions.require(regionId);
        SessionPrincipal principal = currentActor.requirePrincipal();
        if (!permissions.isMemberOf(principal, regionId)) {
            permissions.requireMayAdministrateRegion(principal, regionId);
        }
    }
}
