package network.foodsharing.region.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** REGION history (region admin log, plan §10.2): ambassadors / FS management of the region or above (ORGA always). */
@Component
public class RegionHistoryAccessRule implements HistoryAccessRule {

    private final PermissionService permissionService;

    public RegionHistoryAccessRule(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.REGION;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return permissionService.mayAdministrateRegion(actor, subjectId);
    }
}
