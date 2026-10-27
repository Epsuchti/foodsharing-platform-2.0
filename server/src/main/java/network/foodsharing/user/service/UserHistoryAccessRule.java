package network.foodsharing.user.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * USER history (profile change, verification, passport and quiz session history, plan §10.2): the user themself and
 * whoever may administrate the user (ambassadors / FS management of one of the user's regions; ORGA via the policy).
 */
@Component
public class UserHistoryAccessRule implements HistoryAccessRule {

    private final PermissionService permissionService;

    public UserHistoryAccessRule(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.USER;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return actor != null && (actor.userId().equals(subjectId) || permissionService.mayAdministrateUser(actor, subjectId));
    }
}
