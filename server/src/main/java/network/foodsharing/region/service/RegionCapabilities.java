package network.foodsharing.region.service;

import network.foodsharing.security.NavCapabilities;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Menu capabilities of the region module (decision 162): Neulinge, Verifizierung and Mitglieder for those who
 * administrate their home region (ambassador, FSMANAGEMENT admin, ORGA) or hold CAN_MANAGE_USERS somewhere. Admins
 * of other working groups get none of them.
 */
@Component
@Order(15)
public class RegionCapabilities implements SessionInfoEnricher {

    private final PermissionService permissions;

    public RegionCapabilities(PermissionService permissions) {
        this.permissions = permissions;
    }

    @Override
    public void enrich(SessionPrincipal principal, SessionInfoBuilder builder) {
        if (managesUsers(principal, builder)) {
            builder.capability(NavCapabilities.MANAGE_NEWCOMERS)
                    .capability(NavCapabilities.VERIFY)
                    .capability(NavCapabilities.MANAGE_MEMBERS);
        }
    }

    private boolean managesUsers(SessionPrincipal principal, SessionInfoBuilder builder) {
        if (permissions.isOrga(principal)) {
            return true;
        }
        if (builder.holdsPermissionAnywhere(Permission.CAN_MANAGE_USERS.name())) {
            return true;
        }
        return builder.adminOfAnyUnit() && builder.homeRegionId() != null
                && permissions.mayAdministrateRegion(principal, builder.homeRegionId());
    }
}
