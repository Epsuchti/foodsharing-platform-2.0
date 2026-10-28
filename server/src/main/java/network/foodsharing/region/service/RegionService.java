package network.foodsharing.region.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read API of the region tree for other modules (docs/conventions.md A15: {@link #zoneOf}). */
@Service
public class RegionService {

    private final RegionRepository regions;
    private final RegionClosureRepository closure;
    private final AppProperties properties;
    private final network.foodsharing.region.repository.RegionFunctionRepository functions;
    private final network.foodsharing.region.repository.RegionAdminRepository admins;
    private final network.foodsharing.region.repository.RegionMembershipRepository memberships;
    private final network.foodsharing.region.repository.RegionPinRepository pins;

    public RegionService(RegionRepository regions, RegionClosureRepository closure, AppProperties properties,
                         network.foodsharing.region.repository.RegionFunctionRepository functions,
                         network.foodsharing.region.repository.RegionAdminRepository admins,
                         network.foodsharing.region.repository.RegionMembershipRepository memberships,
                         network.foodsharing.region.repository.RegionPinRepository pins) {
        this.memberships = memberships;
        this.pins = pins;
        this.regions = regions;
        this.closure = closure;
        this.properties = properties;
        this.functions = functions;
        this.admins = admins;
    }

    /** Is the user admin of a working group that holds this function in any region (e.g. PR for content)? */
    @Transactional(readOnly = true)
    public boolean isFunctionGroupAdminAnywhere(UUID userId, network.foodsharing.security.WorkingGroupFunction function) {
        java.util.Set<UUID> adminOf = admins.findRegionIds(userId);
        return !adminOf.isEmpty() && functions.findByGroupIdIn(adminOf).stream()
                .anyMatch(assignment -> assignment.getFunction() == function);
    }

    /** Admins of every working group that holds this function in any region (e.g. PR admins for the blog check). */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> functionGroupAdminIds(network.foodsharing.security.WorkingGroupFunction function) {
        java.util.Set<UUID> result = new java.util.TreeSet<>();
        functions.findByFunction(function).forEach(assignment -> result.addAll(admins.findUserIds(assignment.getGroupId())));
        return result;
    }

    /** Active working groups flagged for the public team page (C16), in sort order. */
    @Transactional(readOnly = true)
    public List<Region> teamPageGroups() {
        return regions.findByTeamPageTrueAndActiveTrueOrderBySortOrderAscNameAsc();
    }

    /** Active regions (no working groups) with at least one ambassador (explainer counter). */
    @Transactional(readOnly = true)
    public long countRegionsWithAmbassadors() {
        return admins.countRegionsWithAdmins();
    }

    @Transactional(readOnly = true)
    public Optional<Region> find(UUID regionId) {
        return regionId == null ? Optional.empty() : regions.findById(regionId);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Region require(UUID regionId) {
        return find(regionId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Region requireBySlug(String slug) {
        return regions.findBySlug(slug).orElseThrow(() -> ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Region> findAll(Collection<UUID> regionIds) {
        return regionIds.isEmpty() ? List.of() : regions.findByIdIn(regionIds);
    }

    /** Time zone of a region (its own, else {@code app.region.default-time-zone}). */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID regionId) {
        return find(regionId).map(region -> zone(region.getTimeZone()))
                .orElseGet(() -> zone(properties.getRegion().getDefaultTimeZone()));
    }

    /** The region's ancestors including itself, root first. */
    @Transactional(readOnly = true)
    public List<Region> ancestors(UUID regionId) {
        return regions.findAncestors(regionId);
    }

    /** Ids of the region and all regions / groups below it. */
    @Transactional(readOnly = true)
    public List<UUID> subtreeIds(UUID regionId) {
        return closure.subtree(regionId);
    }

    /** Regions / groups the user is admin (ambassador) of. */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> adminRegionIds(UUID userId) {
        return admins.findRegionIds(userId);
    }

    /** Direct admins (ambassadors) of the region / group (mailbox readers, A35). */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> adminIds(UUID regionId) {
        return admins.findUserIds(regionId);
    }

    /** Active direct members of the region / group (event invitations, poll eligibility). */
    @Transactional(readOnly = true)
    public List<UUID> activeMemberIds(UUID regionId) {
        return memberships.findActiveUserIds(regionId);
    }

    /** Regions and groups the user is an active member of (open polls). */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeRegionIdsOf(UUID userId) {
        return memberships.findActiveRegionIds(userId);
    }

    /** Active members of the region and every region / group below it (newsletter recipients, N7). */
    @Transactional(readOnly = true)
    public List<UUID> memberIdsOfSubtree(UUID regionId) {
        return memberships.findActiveUserIdsIn(subtreeIds(regionId));
    }

    /**
     * The given users that are active members of at least one of the regions (region statistics contributors count
     * their rows per member without reading {@code region_membership} themselves).
     */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembersAmong(Collection<UUID> regionIds, Collection<UUID> userIds) {
        if (regionIds.isEmpty() || userIds.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.Set<UUID> result = new java.util.HashSet<>();
        List<UUID> users = List.copyOf(new java.util.LinkedHashSet<>(userIds));
        for (int from = 0; from < users.size(); from += 1000) {
            result.addAll(memberships.findActiveUserIdsAmong(regionIds,
                    users.subList(from, Math.min(users.size(), from + 1000))));
        }
        return result;
    }

    /** Map layer (M1): active, non-group regions with coordinates. */
    @Transactional(readOnly = true)
    public List<Region> regionsWithCoordinates() {
        return regions.findAll().stream().filter(r -> r.isActive() && r.getLatitude() != null
                && r.getLongitude() != null && r.getType() != network.foodsharing.region.domain.UnitType.WORKING_GROUP)
                .toList();
    }

    /** Map layer (M1): active region pins. */
    @Transactional(readOnly = true)
    public List<network.foodsharing.region.domain.RegionPin> activePins() {
        return pins.findAll().stream()
                .filter(p -> p.getStatus() == network.foodsharing.region.domain.RegionPinStatus.ACTIVE).toList();
    }

    @Transactional(readOnly = true)
    public long activeMemberCount(UUID regionId) {
        return memberships.countByRegionIdAndActiveTrue(regionId);
    }

    /** Direct children, regions before working groups, by sort order and name. */
    @Transactional(readOnly = true)
    public List<Region> children(UUID regionId, boolean includeGroups) {
        List<Region> children = regionId == null ? regions.findByParentIdIsNullOrderBySortOrderAscNameAsc()
                : regions.findByParentIdOrderBySortOrderAscNameAsc(regionId);
        return children.stream().filter(region -> includeGroups || !region.isGroup()).toList();
    }

    /** Working groups whose {@code quiz_kinds} list contains the quiz kind name (quiz editor groups, A14 decision). */
    @Transactional(readOnly = true)
    public List<Region> groupsWithQuizKind(String quizKind) {
        return regions.findAll().stream().filter(Region::isGroup)
                .filter(group -> group.getQuizKinds() != null && java.util.Arrays.stream(group.getQuizKinds().split(","))
                        .map(String::strip).anyMatch(quizKind::equals))
                .toList();
    }

    public static boolean isValidZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException | NullPointerException e) {
            return false;
        }
    }

    private ZoneId zone(String zone) {
        return isValidZone(zone) ? ZoneId.of(zone) : ZoneId.of("Europe/Zurich");
    }
}
