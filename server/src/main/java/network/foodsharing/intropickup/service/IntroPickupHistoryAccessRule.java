package network.foodsharing.intropickup.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * INTRO_PICKUP history: the guide of the EinAb, administrators of its region and CAN_MANAGE_USERS holders there (ORGA
 * always). Entries whose subject is not an EinAb (profile, templates, subscriptions, aliases) are ORGA-only here.
 */
@Component
public class IntroPickupHistoryAccessRule implements HistoryAccessRule {

    private final IntroPickupRepository pickups;
    private final PermissionService permissions;

    public IntroPickupHistoryAccessRule(IntroPickupRepository pickups, PermissionService permissions) {
        this.pickups = pickups;
        this.permissions = permissions;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.INTRO_PICKUP;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        if (permissions.isOrga(actor)) {
            return true;
        }
        return pickups.findById(subjectId).map(pickup -> actor.userId().equals(pickup.getGuideId())
                || permissions.mayAdministrateRegion(actor, pickup.getRegionId())
                || permissions.has(actor, Permission.CAN_MANAGE_USERS, pickup.getRegionId())).orElse(false);
    }
}
