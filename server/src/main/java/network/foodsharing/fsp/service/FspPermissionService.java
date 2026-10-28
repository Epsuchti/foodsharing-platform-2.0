package network.foodsharing.fsp.service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Collects the {@link FspRules.Facts} of a viewer (FS1). */
@Service
@Transactional(readOnly = true)
public class FspPermissionService {

    private final PermissionService permissions;
    private final RegionReadService regionRead;
    private final RegionService regions;
    private final FspFollowerRepository followers;

    public FspPermissionService(PermissionService permissions, RegionReadService regionRead, RegionService regions,
                                FspFollowerRepository followers) {
        this.permissions = permissions;
        this.regionRead = regionRead;
        this.regions = regions;
        this.followers = followers;
    }

    /** ORGA, FSP group admins, or region administrators (incl. ancestors) when the region has no FSP group. */
    public boolean mayAdd(SessionPrincipal actor, UUID regionId) {
        if (permissions.hasFunction(actor, WorkingGroupFunction.FSP, regionId)) {
            return true;
        }
        return fspGroup(regionId).isEmpty() && permissions.mayAdministrateRegion(actor, regionId);
    }

    public boolean isMember(SessionPrincipal actor, UUID regionId) {
        return permissions.isMemberOf(actor, regionId);
    }

    public FspRules.Facts facts(SessionPrincipal actor, FoodSharePoint fsp) {
        Optional<FspFollower> row = followers.findByFspIdAndUserId(fsp.getId(), actor.userId());
        boolean manager = row.map(r -> r.getRole() == FspFollowerRole.MANAGER).orElse(false);
        return new FspRules.Facts(mayAdd(actor, fsp.getRegionId()), isMember(actor, fsp.getRegionId()), manager,
                row.isPresent(), actor.userId().equals(fsp.getCreatedByUserId()), fsp.getStatus());
    }

    /** @throws ApiException 404 FSP_NOT_FOUND when the viewer may not see the FSP */
    public FspRules.Facts requireVisible(SessionPrincipal actor, FoodSharePoint fsp) {
        FspRules.Facts facts = facts(actor, fsp);
        if (!FspRules.mayView(facts)) {
            throw ApiException.notFound(ApiErrorCode.FSP_NOT_FOUND);
        }
        return facts;
    }

    public static void require(boolean allowed) {
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * Recipients of the {@code FSP_ACTIVATE} bell: admins of the FSP function group, else the region's ambassadors.
     */
    public Set<UUID> activators(UUID regionId) {
        Set<UUID> result = new LinkedHashSet<>(fspGroup(regionId).map(g -> regions.adminIds(g.getId()))
                .orElseGet(() -> regions.adminIds(regionId)));
        return result;
    }

    private Optional<Region> fspGroup(UUID regionId) {
        return regionRead.functionGroups(regionId).stream().filter(g -> g.function() == WorkingGroupFunction.FSP)
                .map(RegionReadService.FunctionGroup::group).findFirst();
    }
}
