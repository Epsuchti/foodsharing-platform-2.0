package network.foodsharing.report.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collects the facts for {@link ReportRules} (G1, upstream {@code ReportPermissions}). Report handlers of a region =
 * admins of its REPORT working group (else its ambassadors) plus admins of its ARBITRATION group; handlers of an
 * ancestor region handle the reports of the regions below it as well.
 */
@Service
@Transactional(readOnly = true)
public class ReportPermissionService {

    private final PermissionService permissions;
    private final RegionService regions;
    private final RegionReadService regionRead;
    private final RegionOptionsService options;
    private final MembershipService memberships;

    public ReportPermissionService(PermissionService permissions, RegionService regions, RegionReadService regionRead,
                                   RegionOptionsService options, MembershipService memberships) {
        this.permissions = permissions;
        this.regions = regions;
        this.regionRead = regionRead;
        this.options = options;
        this.memberships = memberships;
    }

    /** Direct handlers of the region (bell recipients): REPORT group admins (else ambassadors) + ARBITRATION admins. */
    public Set<UUID> handlers(UUID regionId) {
        Set<UUID> result = new LinkedHashSet<>(functionGroup(regionId, WorkingGroupFunction.REPORT)
                .map(g -> regions.adminIds(g.getId())).orElseGet(() -> regions.adminIds(regionId)));
        functionGroup(regionId, WorkingGroupFunction.ARBITRATION).ifPresent(g -> result.addAll(regions.adminIds(g.getId())));
        return result;
    }

    /** ORGA, or a handler of the region or one of its ancestors. */
    public boolean mayHandle(SessionPrincipal actor, UUID regionId) {
        if (actor == null || regionId == null) {
            return false;
        }
        boolean handler = false;
        for (UUID id : chain(regionId)) {
            if (handlers(id).contains(actor.userId())) {
                handler = true;
                break;
            }
        }
        return ReportRules.mayHandle(permissions.isOrga(actor), handler);
    }

    /** @throws ApiException 403 REPORTS_NOT_ACCESSIBLE */
    public void requireHandler(SessionPrincipal actor, UUID regionId) {
        if (!mayHandle(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.REPORTS_NOT_ACCESSIBLE);
        }
    }

    /** ORGA, or report handler in one of the user's regions. */
    public boolean mayAccessReportsForUser(SessionPrincipal actor, UUID userId) {
        if (permissions.isOrga(actor)) {
            return true;
        }
        return memberships.units(userId).stream().filter(MembershipService.Unit::member)
                .filter(u -> !u.region().isGroup()).anyMatch(u -> mayHandle(actor, u.region().getId()));
    }

    public boolean isOrga(SessionPrincipal actor) {
        return permissions.isOrga(actor);
    }

    public boolean isMember(SessionPrincipal actor, UUID regionId) {
        return permissions.isMemberOf(actor, regionId);
    }

    /** Facts for filing a report about {@code reported}. */
    public ReportRules.ReporterFacts reporterFacts(User reporter, User reported) {
        boolean self = reporter.getId().equals(reported.getId());
        return new ReportRules.ReporterFacts(self, reporter.isVerified(),
                !self && reportButtonInCommonRegion(reporter.getId(), reported.getId()));
    }

    /** Options of the report's region (no membership check: the report dialog reads them, G3). */
    public RegionOptionsService.Options regionOptions(UUID regionId) {
        return options.read(regionId);
    }

    private boolean reportButtonInCommonRegion(UUID reporterId, UUID reportedId) {
        Set<UUID> common = new HashSet<>();
        memberships.units(reporterId).stream().filter(MembershipService.Unit::member)
                .filter(u -> !u.region().isGroup()).forEach(u -> common.add(u.region().getId()));
        Set<UUID> theirs = new HashSet<>();
        memberships.units(reportedId).stream().filter(MembershipService.Unit::member)
                .forEach(u -> theirs.add(u.region().getId()));
        common.retainAll(theirs);
        return common.stream().anyMatch(regionId -> options.read(regionId).enableReportButton());
    }

    /** Mediation team: MEDIATION group admins and members of the region or the nearest ancestor, else ambassadors. */
    public Optional<Region> mediationGroup(UUID regionId) {
        for (UUID id : chain(regionId).reversed()) {
            Optional<Region> group = functionGroup(id, WorkingGroupFunction.MEDIATION);
            if (group.isPresent()) {
                return group;
            }
        }
        return Optional.empty();
    }

    /** The ancestors from the root down, then the region itself. */
    private List<UUID> chain(UUID regionId) {
        List<UUID> ids = new ArrayList<>();
        regions.ancestors(regionId).forEach(r -> {
            if (!r.getId().equals(regionId)) {
                ids.add(r.getId());
            }
        });
        ids.add(regionId);
        return ids;
    }

    private Optional<Region> functionGroup(UUID regionId, WorkingGroupFunction function) {
        return regionRead.functionGroups(regionId).stream().filter(g -> g.function() == function)
                .map(RegionReadService.FunctionGroup::group).findFirst();
    }
}
