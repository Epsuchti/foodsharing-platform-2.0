package network.foodsharing.region.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** GROUP history: admins of the group and administrators of its parent region (ORGA always). */
@Component
public class GroupHistoryAccessRule implements HistoryAccessRule {

    private final PermissionService permissionService;
    private final RegionRepository regions;

    public GroupHistoryAccessRule(PermissionService permissionService, RegionRepository regions) {
        this.permissionService = permissionService;
        this.regions = regions;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.GROUP;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        if (permissionService.isAmbassador(actor, subjectId)) {
            return true;
        }
        UUID parentId = regions.findById(subjectId).map(Region::getParentId).orElse(null);
        return parentId != null && permissionService.mayAdministrateRegion(actor, parentId);
    }
}
