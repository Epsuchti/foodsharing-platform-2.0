package network.foodsharing.user.service;

import java.util.UUID;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of user management (AD1); the mutations live in {@link UserService}. */
@Service
public class UserAdminService {

    private final UserService userService;
    private final CurrentActorService currentActorService;
    private final PermissionService permissionService;

    public UserAdminService(UserService userService, CurrentActorService currentActorService,
                            PermissionService permissionService) {
        this.userService = userService;
        this.currentActorService = currentActorService;
        this.permissionService = permissionService;
    }

    /**
     * Full profile for administrators (also of disabled accounts).
     *
     * @throws network.foodsharing.common.error.ApiException 403 unless mayAdministrateUser, 404 USER_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public User getForAdmin(UUID userId) {
        permissionService.requireMayAdministrateUser(currentActorService.requirePrincipal(), userId);
        return userService.require(userId);
    }

    /** The user themself or an administrator of the user; 404 when the user does not exist. */
    @Transactional(readOnly = true)
    public void requireReadableUser(UUID userId) {
        SessionPrincipal actor = currentActorService.requirePrincipal();
        if (!actor.userId().equals(userId)) {
            permissionService.requireMayAdministrateUser(actor, userId);
        }
        userService.require(userId);
    }
}
