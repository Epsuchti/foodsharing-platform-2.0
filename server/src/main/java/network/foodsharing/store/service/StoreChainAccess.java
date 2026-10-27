package network.foodsharing.store.service;

import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Chain administration right: ORGA, or admin of a working group that has the STORE_CHAINS function anywhere. */
@Component
public class StoreChainAccess {

    private final PermissionService permissions;
    private final RegionService regions;

    public StoreChainAccess(PermissionService permissions, RegionService regions) {
        this.permissions = permissions;
        this.regions = regions;
    }

    @Transactional(readOnly = true)
    public boolean mayAdministrateChains(SessionPrincipal actor) {
        return actor != null && (permissions.isOrga(actor)
                || regions.isFunctionGroupAdminAnywhere(actor.userId(), WorkingGroupFunction.STORE_CHAINS));
    }
}
