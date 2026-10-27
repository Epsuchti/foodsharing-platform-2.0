package network.foodsharing.store.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place store rights are decided (A19, port of upstream {@code StorePermissions}). Store coordination: ORGA;
 * else, when the store's region has a STORES_COORDINATION group, only that group's admins ("store coordination
 * supersedes ambassadors"); else the ambassadors of the region or one of its ancestors.
 */
@Service
@Transactional(readOnly = true)
public class StorePermissionService {

    /** Every right of one actor in one store (the StorePermissions model). */
    public record Rights(boolean mayAccess, boolean mayReadWall, boolean mayPostWall, boolean mayDeleteAnyWallPost,
                         boolean mayEditStore, boolean mayEditTeam, boolean maySeePhoneNumbers,
                         boolean maySeeMemberDistance, boolean maySeePickups, boolean mayEditPickups,
                         boolean mayConfirmPickups, boolean maySeePickupHistory, boolean maySeeStoreLog,
                         boolean mayDelete, boolean mayApply, boolean mayLeave, boolean mayChatTeam,
                         boolean mayChatJumper, boolean isKam, boolean isManager, boolean coordinate,
                         UserTeamStatus teamStatus) {
    }

    private static final Set<UserTeamStatus> TEAM = Set.of(UserTeamStatus.MEMBER, UserTeamStatus.COORDINATOR);
    private static final Set<UserTeamStatus> ACCESS = Set.of(UserTeamStatus.WAITING_LIST, UserTeamStatus.MEMBER,
            UserTeamStatus.COORDINATOR);

    private final StoreMembershipRepository memberships;
    private final StoreChainKamRepository kams;
    private final RegionService regions;
    private final RegionReadService regionReads;
    private final RegionPermissionResolver resolver;
    private final PermissionService permissions;
    private final UserService users;

    public StorePermissionService(StoreMembershipRepository memberships, StoreChainKamRepository kams,
                                  RegionService regions, RegionReadService regionReads,
                                  RegionPermissionResolver resolver, PermissionService permissions,
                                  UserService users) {
        this.memberships = memberships;
        this.kams = kams;
        this.regions = regions;
        this.regionReads = regionReads;
        this.resolver = resolver;
        this.permissions = permissions;
        this.users = users;
    }

    public static UserTeamStatus status(Optional<StoreMembership> membership) {
        if (membership.isEmpty()) {
            return UserTeamStatus.NO_MEMBER;
        }
        return switch (membership.get().getStatus()) {
            case APPLIED -> UserTeamStatus.APPLIED;
            case INVITED -> UserTeamStatus.INVITED;
            case JUMPER -> UserTeamStatus.WAITING_LIST;
            case MEMBER -> membership.get().isManager() ? UserTeamStatus.COORDINATOR : UserTeamStatus.MEMBER;
        };
    }

    public UserTeamStatus teamStatus(UUID userId, UUID storeId) {
        return userId == null ? UserTeamStatus.NO_MEMBER
                : status(memberships.findByStoreIdAndUserId(storeId, userId));
    }

    /** ORGA; else admins of the region's STORES_COORDINATION group; else ambassadors of the region or an ancestor. */
    public boolean mayCoordinateRegionStores(SessionPrincipal actor, UUID regionId) {
        if (actor == null) {
            return false;
        }
        if (permissions.isOrga(actor)) {
            return true;
        }
        Optional<RegionReadService.FunctionGroup> coordination = regionReads.functionGroups(regionId).stream()
                .filter(g -> g.function() == WorkingGroupFunction.STORES_COORDINATION).findFirst();
        if (coordination.isPresent()) {
            return resolver.isAdmin(actor.userId(), coordination.get().group().getId());
        }
        return regions.ancestors(regionId).stream().map(Region::getId)
                .anyMatch(id -> resolver.isAdmin(actor.userId(), id));
    }

    public boolean isKam(SessionPrincipal actor, Store store) {
        return actor != null && store.getChainId() != null
                && PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER)
                && kams.existsByChainIdAndUserId(store.getChainId(), actor.userId());
    }

    public Rights rights(SessionPrincipal actor, Store store) {
        boolean orga = actor != null && permissions.isOrga(actor);
        UserTeamStatus status = teamStatus(actor == null ? null : actor.userId(), store.getId());
        boolean coordinate = mayCoordinateRegionStores(actor, store.getRegionId());
        boolean kam = isKam(actor, store);
        boolean verified = actor != null && users.findActive(actor.userId()).map(User::isVerified).orElse(false);
        boolean storeManagerRole = actor != null && PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER);
        boolean access = orga || ACCESS.contains(status) || kam || coordinate;
        boolean wall = orga || TEAM.contains(status) || coordinate;
        boolean edit = orga || (storeManagerRole && (status == UserTeamStatus.COORDINATOR || coordinate));
        boolean pickups = (wall && (verified || orga)) || kam;
        boolean open = store.getTeamStatus() != TeamSearchStatus.CLOSED;
        return new Rights(access, wall, wall, orga, edit, edit, verified && wall, edit, pickups, edit, edit,
                edit || kam, edit, coordinate, status == UserTeamStatus.NO_MEMBER && open && actor != null,
                status == UserTeamStatus.MEMBER || status == UserTeamStatus.WAITING_LIST
                        || status == UserTeamStatus.APPLIED,
                TEAM.contains(status),
                status == UserTeamStatus.COORDINATOR || status == UserTeamStatus.WAITING_LIST, kam, status == UserTeamStatus.COORDINATOR,
                coordinate, status);
    }

    /** ORGA, or role ≥ STORE_MANAGER and member of the region. */
    public boolean mayCreateStore(SessionPrincipal actor, UUID regionId) {
        return actor != null && (permissions.isOrga(actor)
                || (PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER)
                && resolver.isMember(actor.userId(), regionId)));
    }

    /** Self, mayEditStore, or administrator of the user. */
    public boolean mayRemovePickupUser(SessionPrincipal actor, Store store, UUID userId) {
        return actor != null && (actor.userId().equals(userId) || rights(actor, store).mayEditStore()
                || permissions.mayAdministrateUser(actor, userId));
    }

    public static void require(boolean allowed) {
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
