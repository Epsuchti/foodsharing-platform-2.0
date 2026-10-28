package network.foodsharing.achievement.service;

import java.util.UUID;
import network.foodsharing.achievement.domain.Achievement;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;

/** Facts for {@link AchievementRules} (who administers / is a member of the achievement's region). */
@Service
public class AchievementPermissionService {

    private final PermissionService permissions;
    private final MembershipService memberships;

    public AchievementPermissionService(PermissionService permissions, MembershipService memberships) {
        this.permissions = permissions;
        this.memberships = memberships;
    }

    /** A principal for a viewer known only by id (profile SPI). */
    public static SessionPrincipal principalOf(User user) {
        return new SessionPrincipal(user.getId(), null, user.getRole(), user.getEmail());
    }

    /** ORGA for global achievements, else ORGA or region administrator. */
    public boolean mayAdminister(SessionPrincipal actor, UUID regionId) {
        return regionId == null ? permissions.isOrga(actor) : permissions.mayAdministrateRegion(actor, regionId);
    }

    public boolean mayAdminister(SessionPrincipal actor, Achievement achievement) {
        return mayAdminister(actor, achievement.getRegionId());
    }

    public void requireAdminister(SessionPrincipal actor, UUID regionId) {
        if (!mayAdminister(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    /** Every member counts for global achievements. */
    public boolean isMember(SessionPrincipal actor, UUID regionId) {
        return regionId == null || permissions.isMemberOf(actor, regionId);
    }

    /** Recipient check for awarding: global = any active member, regional = active member of the region. */
    public boolean isActiveMember(UUID userId, UUID regionId) {
        return regionId == null || memberships.units(userId).stream()
                .anyMatch(unit -> unit.member() && unit.region().getId().equals(regionId));
    }

    public AchievementRules.Viewer viewer(SessionPrincipal actor, UUID regionId, UUID recipientId) {
        return new AchievementRules.Viewer(mayAdminister(actor, regionId), isMember(actor, regionId),
                permissions.hasRole(actor, Role.STORE_MANAGER), actor != null && actor.userId().equals(recipientId));
    }
}
