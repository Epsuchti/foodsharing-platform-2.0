package network.foodsharing.region.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Region page data (plan §13.2 R3): detail, children, search. Readable by every signed-in user. */
@Service
@Transactional(readOnly = true)
public class RegionReadService {

    public record FunctionGroup(WorkingGroupFunction function, Region group) {
    }

    public record Membership(boolean member, boolean admin, boolean homeRegion, Instant joinedAt) {
    }

    public record RegionDetailView(Region region, Region parent, List<User> ambassadors, Region welcomeTeam,
                                   List<FunctionGroup> functions, int memberCount, Membership membership,
                                   String conferenceUrl, RegionStatisticsService.StatisticsView statistics,
                                   boolean mayCoordinateStores, boolean mayHandleReports) {
    }

    private final RegionService regionService;
    private final RegionRepository regions;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionFunctionRepository functions;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final ConferenceLinkService conference;
    private final RegionStatisticsService statistics;
    private final ObjectProvider<ReportHandlerRule> reportHandlerRule;

    public RegionReadService(RegionService regionService, RegionRepository regions,
                             RegionMembershipRepository memberships, RegionAdminRepository admins,
                             RegionFunctionRepository functions, UserService users, CurrentActorService currentActor,
                             ConferenceLinkService conference, RegionStatisticsService statistics,
                             ObjectProvider<ReportHandlerRule> reportHandlerRule) {
        this.regionService = regionService;
        this.regions = regions;
        this.memberships = memberships;
        this.admins = admins;
        this.functions = functions;
        this.users = users;
        this.currentActor = currentActor;
        this.conference = conference;
        this.statistics = statistics;
        this.reportHandlerRule = reportHandlerRule;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND */
    public RegionDetailView detail(UUID regionId) {
        SessionPrincipal principal = currentActor.requirePrincipal();
        Region region = regionService.require(regionId);
        Region parent = region.getParentId() == null ? null : regions.findById(region.getParentId()).orElse(null);
        List<User> ambassadors = ambassadorsOf(region.getId());
        List<FunctionGroup> functionGroups = functionGroups(region.getId());
        Region welcome = functionGroups.stream().filter(fg -> fg.function() == WorkingGroupFunction.WELCOME)
                .map(FunctionGroup::group).findFirst().orElse(null);
        Optional<RegionMembership> own = memberships.findByUserIdAndRegionId(principal.userId(), regionId)
                .filter(RegionMembership::isActive);
        boolean admin = admins.existsByUserIdAndRegionId(principal.userId(), regionId);
        UUID home = users.findActive(principal.userId()).map(User::getHomeRegionId).orElse(null);
        Membership membership = new Membership(own.isPresent(), admin, regionId.equals(home),
                own.map(RegionMembership::getJoinedAt).orElse(null));
        boolean orga = currentActor.isOrga();
        boolean insider = own.isPresent() || admin || orga;
        UUID coordinationGroup = functionGroups.stream()
                .filter(fg -> fg.function() == WorkingGroupFunction.STORES_COORDINATION)
                .map(fg -> fg.group().getId()).findFirst().orElse(null);
        boolean mayCoordinateStores = StoreCoordinationRule.mayCoordinate(orga, coordinationGroup,
                () -> regionService.ancestors(regionId).stream().map(Region::getId).toList(),
                id -> admins.existsByUserIdAndRegionId(principal.userId(), id));
        return new RegionDetailView(region, parent, ambassadors, welcome, functionGroups,
                (int) memberships.countByRegionIdAndActiveTrue(regionId), membership,
                insider ? conference.linkOf(region) : null, insider ? statistics.viewOf(region) : null,
                mayCoordinateStores, mayHandleReports(principal, regionId, orga));
    }

    /** A51 handler rule from the report module; without it (slice tests) only ORGA. */
    static boolean mayHandleReports(ReportHandlerRule rule, SessionPrincipal principal, UUID regionId, boolean orga) {
        return rule == null ? orga : rule.mayHandleReports(principal, regionId);
    }

    private boolean mayHandleReports(SessionPrincipal principal, UUID regionId, boolean orga) {
        return mayHandleReports(reportHandlerRule.getIfAvailable(), principal, regionId, orga);
    }

    public List<User> ambassadorsOf(UUID regionId) {
        return users.findActive(admins.findUserIds(regionId)).stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<FunctionGroup> functionGroups(UUID regionId) {
        List<RegionFunction> assignments = functions.findByRegionId(regionId);
        Map<UUID, Region> groups = regions.findByIdIn(assignments.stream().map(RegionFunction::getGroupId).toList())
                .stream().collect(Collectors.toMap(Region::getId, Function.identity()));
        return assignments.stream().filter(a -> groups.containsKey(a.getGroupId()))
                .map(a -> new FunctionGroup(a.getFunction(), groups.get(a.getGroupId())))
                .sorted(Comparator.comparing(FunctionGroup::function)).toList();
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND */
    public List<Region> children(UUID regionId, boolean includeGroups) {
        regionService.require(regionId);
        return regionService.children(regionId, includeGroups);
    }

    public Page<Region> search(String q, Collection<UnitType> types, int page, int size) {
        Collection<UnitType> filter = types == null || types.isEmpty() ? Arrays.asList(UnitType.values()) : types;
        String pattern = q == null || q.isBlank() ? null : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        return regions.search(pattern, filter, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }
}
