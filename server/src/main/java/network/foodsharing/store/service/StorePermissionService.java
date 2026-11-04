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
import network.foodsharing.region.service.StoreCoordinationRule;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
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

        /** No right at all: people who may not know the stores, whatever rows exist for them (S17). */
        static Rights none(UserTeamStatus teamStatus) {
            return new Rights(false, false, false, false, false, false, false, false, false, false, false, false,
                    false, false, false, false, false, false, false, false, false, teamStatus);
        }

        /** ORGA or this store's manager ({@code mayDeleteAnyWallPost} is the ORGA right); S17 D6. */
        public boolean mayChangePublicNaming() {
            return PublicNamingRules.mayChange(mayDeleteAnyWallPost, isManager);
        }
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
    private final StoreVisibility visibility;

    public StorePermissionService(StoreMembershipRepository memberships, StoreChainKamRepository kams,
                                  RegionService regions, RegionReadService regionReads,
                                  RegionPermissionResolver resolver, PermissionService permissions,
                                  StoreVisibility visibility) {
        this.memberships = memberships;
        this.kams = kams;
        this.regions = regions;
        this.regionReads = regionReads;
        this.resolver = resolver;
        this.permissions = permissions;
        this.visibility = visibility;
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
        boolean orga = permissions.isOrga(actor);
        UUID coordinationGroup = orga ? null : regionReads.functionGroups(regionId).stream()
                .filter(g -> g.function() == WorkingGroupFunction.STORES_COORDINATION)
                .map(g -> g.group().getId()).findFirst().orElse(null);
        return StoreCoordinationRule.mayCoordinate(orga, coordinationGroup,
                () -> regions.ancestors(regionId).stream().map(Region::getId).toList(),
                id -> resolver.isAdmin(actor.userId(), id));
    }

    public boolean isKam(SessionPrincipal actor, Store store) {
        return actor != null && store.getChainId() != null
                && PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER)
                && kams.existsByChainIdAndUserId(store.getChainId(), actor.userId());
    }

    /**
     * Public store page for non-members (upstream store view for foodsavers of the region): foodsavers who are
     * active members of the store's region see name, address, category, statuses, public info and requirements so
     * they can apply; ambassadors/coordinators already have full access. Only for people who may know stores (S17).
     */
    public boolean mayViewPublic(SessionPrincipal actor, Store store) {
        return actor != null && PermissionService.isAtLeast(actor.role(), Role.FOODSAVER)
                && permissions.isMemberOf(actor, store.getRegionId()) && visibility.mayKnowStores(actor);
    }

    /** A known and an unknown store id look the same to people who may not know stores (S17 D2). @throws ApiException 404 */
    public void requireMayKnowStore(SessionPrincipal actor) {
        if (!visibility.mayKnowStores(actor)) {
            throw ApiException.notFound(ApiErrorCode.STORE_NOT_FOUND);
        }
    }

    public Rights rights(SessionPrincipal actor, Store store) {
        boolean orga = actor != null && permissions.isOrga(actor);
        UserTeamStatus status = teamStatus(actor == null ? null : actor.userId(), store.getId());
        boolean verified = visibility.isVerified(actor);
        if (!StoreVisibility.mayKnow(orga, verified)) {
            return Rights.none(status);
        }
        boolean coordinate = mayCoordinateRegionStores(actor, store.getRegionId());
        boolean kam = isKam(actor, store);
        boolean storeManagerRole = actor != null && PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER);
        boolean access = orga || ACCESS.contains(status) || kam || coordinate;
        boolean wall = orga || TEAM.contains(status) || coordinate;
        boolean edit = orga || (storeManagerRole && (status == UserTeamStatus.COORDINATOR || coordinate));
        boolean pickups = wall || kam;
        boolean open = store.getTeamStatus() != TeamSearchStatus.CLOSED;
        return new Rights(access, wall, wall, orga, edit, edit, verified && wall, edit, pickups, edit, edit,
                edit || kam, edit, coordinate, status == UserTeamStatus.NO_MEMBER && open && actor != null,
                status == UserTeamStatus.MEMBER || status == UserTeamStatus.WAITING_LIST
                        || status == UserTeamStatus.APPLIED,
                TEAM.contains(status),
                status == UserTeamStatus.COORDINATOR || status == UserTeamStatus.WAITING_LIST, kam, status == UserTeamStatus.COORDINATOR,
                coordinate, status);
    }

    /** mayEditStore(store) of the actor (automation, store alert chats; decision 165). */
    public boolean mayEditStore(SessionPrincipal actor, Store store) {
        return actor != null && rights(actor, store).mayEditStore();
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
