package network.foodsharing.region.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionJoinMode;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region membership (plan §13.2 R2): join / leave, home region, nightly parent memberships.
 *
 * <p>Join rules (docs/conventions.md A10): only "accessible" region types (PART_OF_TOWN, CITY, REGION, DISTRICT);
 * any signed-in user may join (deviation from upstream {@code mayJoinRegion}, which requires FOODSAVER, because
 * journey step 2 happens as FOODSHARER); regions with join mode APPLICATION refuse direct joins. The first region a
 * user joins becomes the home region. A join publishes {@link RegionMemberJoined}; the welcome bell goes to the admins
 * of the region's WELCOME group, else to its ambassadors ({@link #welcomeRecipients}).
 */
@Service
public class MembershipService {

    /** Domain event after a direct join (bell to the welcome team after commit). */
    public record RegionMemberJoined(UUID regionId, UUID userId) {
    }

    private final RegionRepository regions;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionFunctionRepository functions;
    private final RegionClosureRepository closure;
    private final UserService users;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MembershipService(RegionRepository regions, RegionMembershipRepository memberships,
                             RegionAdminRepository admins, RegionFunctionRepository functions,
                             RegionClosureRepository closure, UserService users, HistoryService history,
                             ApplicationEventPublisher events, Clock clock) {
        this.regions = regions;
        this.memberships = memberships;
        this.admins = admins;
        this.functions = functions;
        this.closure = closure;
        this.users = users;
        this.history = history;
        this.events = events;
        this.clock = clock;
    }

    /** A region or group of the user (my units, session info). */
    public record Unit(Region region, boolean member, boolean admin, boolean homeRegion,
                       WorkingGroupFunction function) {
    }

    /**
     * The user joins a region; idempotent for existing members.
     *
     * @throws ApiException 404 REGION_NOT_FOUND / USER_NOT_FOUND, 409 REGION_NOT_JOINABLE,
     *                      409 REGION_REQUIRES_APPLICATION
     */
    @Transactional
    public Unit join(UUID userId, UUID regionId) {
        Region region = regions.findById(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        User user = users.requireActive(userId);
        if (region.getType() == null || !region.getType().isAccessible() || !region.isActive()) {
            throw ApiException.conflict(ApiErrorCode.REGION_NOT_JOINABLE);
        }
        Optional<RegionMembership> existing = memberships.findByUserIdAndRegionId(userId, regionId);
        if (existing.isPresent() && existing.get().isActive()) {
            return unit(region, userId, user.getHomeRegionId());
        }
        if (region.getJoinMode() == RegionJoinMode.APPLICATION) {
            throw ApiException.conflict(ApiErrorCode.REGION_REQUIRES_APPLICATION);
        }
        RegionMembership membership = existing.orElseGet(RegionMembership::new);
        membership.setUserId(userId);
        membership.setRegionId(regionId);
        membership.setActive(true);
        membership.setJoinedAt(clock.instant());
        memberships.save(membership);
        history.record(history.event(RegionAction.MEMBER_JOINED, regionId).secondarySubject(userId)
                .region(regionId).build());
        if (user.getHomeRegionId() == null) {
            users.changeHomeRegion(userId, regionId);
        }
        events.publishEvent(new RegionMemberJoined(regionId, userId));
        return unit(region, userId, user.getHomeRegionId());
    }

    /**
     * The user leaves a region or group. Leaving the home region is only possible when the user is a member of
     * another accessible region, which then becomes the home region (earliest joined first).
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 409 NOT_A_MEMBER, 409 CANNOT_LEAVE_HOME_REGION
     */
    @Transactional
    public void leave(UUID userId, UUID regionId) {
        Region region = regions.findById(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, regionId)
                .filter(RegionMembership::isActive)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_A_MEMBER));
        User user = users.require(userId);
        Optional<UUID> newHome = Optional.empty();
        if (regionId.equals(user.getHomeRegionId())) {
            newHome = otherAccessibleMembership(userId, regionId);
            if (newHome.isEmpty()) {
                throw ApiException.conflict(ApiErrorCode.CANNOT_LEAVE_HOME_REGION);
            }
        }
        boolean wasAdmin = admins.findByUserIdAndRegionId(userId, regionId).map(admin -> {
            admins.delete(admin);
            return true;
        }).orElse(false);
        memberships.delete(membership);
        history.record(history.event(region.isGroup() ? network.foodsharing.region.domain.GroupAction.MEMBER_REMOVED
                        : RegionAction.MEMBER_LEFT, regionId)
                .secondarySubject(userId).region(region.isGroup() ? region.getParentId() : regionId)
                .metadata(Map.of("self", true, "wasAdmin", wasAdmin)).build());
        newHome.ifPresent(home -> users.changeHomeRegion(userId, home));
    }

    /**
     * Sets the home region of the user.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 409 NOT_A_MEMBER, 409 REGION_NOT_JOINABLE (not an accessible type)
     */
    @Transactional
    public Unit setHomeRegion(UUID userId, UUID regionId) {
        Region region = regions.findById(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        if (!memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_A_MEMBER);
        }
        if (region.getType() == null || !region.getType().isAccessible()) {
            throw ApiException.conflict(ApiErrorCode.REGION_NOT_JOINABLE);
        }
        User user = users.changeHomeRegion(userId, regionId);
        return unit(region, userId, user.getHomeRegionId());
    }

    /** Adds a membership found missing by the nightly maintenance (actor SYSTEM). */
    @Transactional
    public boolean addByMaintenance(UUID userId, UUID regionId) {
        if (memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, regionId)) {
            return false;
        }
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, regionId)
                .orElseGet(RegionMembership::new);
        membership.setUserId(userId);
        membership.setRegionId(regionId);
        membership.setActive(true);
        membership.setJoinedAt(clock.instant());
        memberships.save(membership);
        history.record(history.event(RegionAction.MEMBER_ADDED_BY_MAINTENANCE, regionId).secondarySubject(userId)
                .region(regionId).actorSystem().build());
        return true;
    }

    /**
     * Nightly maintenance (upstream MaintenanceRegionCommand): members of a region become members of all its
     * ancestor regions (working groups are not propagated).
     *
     * @return number of memberships added
     */
    @Transactional
    public int addMissingAncestorMemberships() {
        int added = 0;
        for (RegionMembership membership : memberships.findActiveRegionMemberships()) {
            for (UUID ancestor : closure.ancestors(membership.getRegionId())) {
                if (!ancestor.equals(membership.getRegionId())
                        && addByMaintenance(membership.getUserId(), ancestor)) {
                    added++;
                }
            }
        }
        return added;
    }

    /** Regions and groups of the user: memberships plus regions they administrate. */
    @Transactional(readOnly = true)
    public List<Unit> units(UUID userId) {
        UUID homeRegionId = users.findActive(userId).map(User::getHomeRegionId).orElse(null);
        Set<UUID> memberOf = memberships.findActiveRegionIds(userId);
        Set<UUID> adminOf = admins.findRegionIds(userId);
        Set<UUID> ids = new LinkedHashSet<>(memberOf);
        ids.addAll(adminOf);
        Map<UUID, WorkingGroupFunction> groupFunctions = functions.findByGroupIdIn(ids).stream()
                .collect(Collectors.toMap(RegionFunction::getGroupId, RegionFunction::getFunction, (a, b) -> a));
        List<Unit> units = new ArrayList<>();
        for (Region region : regions.findByIdIn(ids)) {
            units.add(new Unit(region, memberOf.contains(region.getId()), adminOf.contains(region.getId()),
                    region.getId().equals(homeRegionId), groupFunctions.get(region.getId())));
        }
        units.sort(Comparator.comparing((Unit unit) -> unit.region().isGroup())
                .thenComparing(unit -> !unit.homeRegion())
                .thenComparing(unit -> unit.region().getName()));
        return units;
    }

    /**
     * Who gets the new-member bell: the admins of the region's WELCOME group, else the region's ambassadors
     * (upstream {@code RegionTransactions::joinRegion}). The joining user never notifies themself.
     */
    @Transactional(readOnly = true)
    public Set<UUID> welcomeRecipients(UUID regionId, UUID joiningUserId) {
        Set<UUID> recipients = functions.findByRegionIdAndFunction(regionId, WorkingGroupFunction.WELCOME)
                .map(group -> admins.findUserIds(group.getGroupId()))
                .filter(set -> !set.isEmpty())
                .orElseGet(() -> admins.findUserIds(regionId));
        Set<UUID> result = new LinkedHashSet<>(recipients);
        result.remove(joiningUserId);
        return result;
    }

    private Optional<UUID> otherAccessibleMembership(UUID userId, UUID excludedRegionId) {
        List<RegionMembership> others = memberships.findByUserIdAndActiveTrue(userId).stream()
                .filter(membership -> !membership.getRegionId().equals(excludedRegionId)).toList();
        Map<UUID, Region> byId = regions.findByIdIn(others.stream().map(RegionMembership::getRegionId).toList())
                .stream().collect(Collectors.toMap(Region::getId, Function.identity()));
        return others.stream()
                .filter(membership -> {
                    Region region = byId.get(membership.getRegionId());
                    return region != null && region.getType() != null && region.getType().isAccessible();
                })
                .min(Comparator.comparing(RegionMembership::getJoinedAt))
                .map(RegionMembership::getRegionId);
    }

    private Unit unit(Region region, UUID userId, UUID homeRegionId) {
        return new Unit(region, true, admins.existsByUserIdAndRegionId(userId, region.getId()),
                region.getId().equals(homeRegionId), null);
    }
}
