package network.foodsharing.region.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.repository.RegionStatisticsRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.Gender;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region statistics (plan §13.2 R3/R13): the nightly cache {@code region_statistics} (member count, active home-region
 * foodsavers = verified, active users with their home region in the subtree who logged in within 6 months, plus
 * {@link RegionStatisticsContributor} figures) and live gender counts / age bands of the members.
 */
@Service
public class RegionStatisticsService {

    /** Age bands of the region page; members without (or with an implausible) date of birth are "unknown". */
    public static final List<String> AGE_BANDS = List.of("<18", "18-25", "26-35", "36-50", "51-65", "66+", "unknown");
    static final Duration ACTIVE_WINDOW = Duration.ofDays(183);

    public record AgeBand(String label, int count) {
    }

    public record StatisticsView(RegionStatistics cached, Map<String, Integer> genderCounts, List<AgeBand> ageBands) {
    }

    private final RegionRepository regions;
    private final RegionStatisticsRepository statistics;
    private final RegionMembershipRepository memberships;
    private final RegionClosureRepository closure;
    private final RegionService regionService;
    private final UserService users;
    private final List<RegionStatisticsContributor> contributors;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final Clock clock;

    public RegionStatisticsService(RegionRepository regions, RegionStatisticsRepository statistics,
                                   RegionMembershipRepository memberships, RegionClosureRepository closure,
                                   RegionService regionService, UserService users,
                                   List<RegionStatisticsContributor> contributors, CurrentActorService currentActor,
                                   PermissionService permissions, Clock clock) {
        this.regions = regions;
        this.statistics = statistics;
        this.memberships = memberships;
        this.closure = closure;
        this.regionService = regionService;
        this.users = users;
        this.contributors = contributors;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.clock = clock;
    }

    /**
     * Cached figures plus live gender counts and age bands; members of the region or its administrators.
     *
     * @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public StatisticsView view(UUID regionId) {
        Region region = regionService.require(regionId);
        var principal = currentActor.requirePrincipal();
        if (!permissions.isMemberOf(principal, regionId)) {
            permissions.requireMayAdministrateRegion(principal, regionId);
        }
        return viewOf(region);
    }

    /** Without permission check (region detail of a member). */
    @Transactional(readOnly = true)
    public StatisticsView viewOf(Region region) {
        RegionStatistics cached = statistics.findByRegionId(region.getId()).orElseGet(() -> {
            RegionStatistics empty = new RegionStatistics();
            empty.setRegionId(region.getId());
            empty.setMemberCount((int) memberships.countByRegionIdAndActiveTrue(region.getId()));
            return empty;
        });
        List<User> members = users.findActive(memberships.findActiveUserIds(region.getId()));
        return new StatisticsView(cached, genderCounts(members), ageBands(members, today(region)));
    }

    /** Recomputes the cache of one region. */
    @Transactional
    @NoHistory(reason = "statistics cache refresh, not a change of region data")
    public RegionStatistics refresh(UUID regionId) {
        Set<UUID> subtree = new HashSet<>(closure.subtree(regionId));
        RegionStatistics row = statistics.findByRegionId(regionId).orElseGet(() -> {
            RegionStatistics created = new RegionStatistics();
            created.setRegionId(regionId);
            return created;
        });
        Instant now = clock.instant();
        row.setMemberCount((int) memberships.countByRegionIdAndActiveTrue(regionId));
        row.setActiveHomeRegionFoodsavers((int) users.findActiveByHomeRegions(subtree).stream()
                .filter(User::isVerified)
                .filter(user -> user.getLastLoginAt() != null && user.getLastLoginAt().isAfter(now.minus(ACTIVE_WINDOW)))
                .count());
        contributors.forEach(contributor -> contributor.contribute(regionId, subtree, row));
        row.setLastModified(now);
        return statistics.save(row);
    }

    /** Refreshes every region (not working groups). */
    @Transactional
    @NoHistory(reason = "statistics cache refresh, not a change of region data")
    public int refreshAll() {
        int count = 0;
        for (Region region : regions.findAll()) {
            if (!region.isGroup()) {
                refresh(region.getId());
                count++;
            }
        }
        return count;
    }

    static Map<String, Integer> genderCounts(List<User> members) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Gender gender : Gender.values()) {
            counts.put(gender.name(), 0);
        }
        members.forEach(user -> counts.merge((user.getGender() == null ? Gender.NOT_SELECTED : user.getGender()).name(),
                1, Integer::sum));
        return counts;
    }

    static List<AgeBand> ageBands(List<User> members, LocalDate today) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        AGE_BANDS.forEach(label -> counts.put(label, 0));
        for (User user : members) {
            counts.merge(band(user.getDateOfBirth(), today), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> new AgeBand(entry.getKey(), entry.getValue())).toList();
    }

    static String band(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null || dateOfBirth.isAfter(today)) {
            return "unknown";
        }
        int age = Period.between(dateOfBirth, today).getYears();
        if (age > 120) {
            return "unknown";
        }
        if (age < 18) {
            return "<18";
        }
        if (age <= 25) {
            return "18-25";
        }
        if (age <= 35) {
            return "26-35";
        }
        if (age <= 50) {
            return "36-50";
        }
        if (age <= 65) {
            return "51-65";
        }
        return "66+";
    }

    private LocalDate today(Region region) {
        return clock.instant().atZone(regionService.zoneOf(region.getId())).toLocalDate();
    }
}
