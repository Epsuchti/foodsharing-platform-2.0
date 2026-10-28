package network.foodsharing.poll.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may vote (upstream {@code VotingTransactions::listUserIds}): FOODSAVERS = active members with role ≥ FOODSAVER,
 * VERIFIED_FOODSAVERS = those that are verified, VERIFIED_FOODSAVERS_HOME_REGION = those whose home region is the
 * poll region, STORE_MANAGERS = members with role ≥ STORE_MANAGER, AMBASSADORS = ambassadors of the region and its
 * sub-regions (for a working group: of its parent region's subtree; upstream {@code getAmbassadorsIDsOfSubregions}).
 */
@Service
@Transactional(readOnly = true)
public class PollEligibilityService {

    private final RegionService regions;
    private final UserService users;

    public PollEligibilityService(RegionService regions, UserService users) {
        this.regions = regions;
        this.users = users;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND */
    public Set<UUID> eligibleUserIds(UUID regionId, PollScope scope) {
        Region region = regions.require(regionId);
        if (scope == PollScope.AMBASSADORS) {
            UUID root = region.getType().isGroup() && region.getParentId() != null ? region.getParentId() : regionId;
            Set<UUID> ambassadors = new LinkedHashSet<>();
            for (Region sub : regions.findAll(regions.subtreeIds(root))) {
                if (!sub.getType().isGroup()) {
                    ambassadors.addAll(regions.adminIds(sub.getId()));
                }
            }
            return ids(users.findActive(ambassadors));
        }
        List<User> members = users.findActive(regions.activeMemberIds(regionId));
        Set<UUID> result = new LinkedHashSet<>();
        for (User user : members) {
            if (matches(user, scope, regionId)) {
                result.add(user.getId());
            }
        }
        return result;
    }

    static boolean matches(User user, PollScope scope, UUID regionId) {
        return switch (scope) {
            case FOODSAVERS -> PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER);
            case VERIFIED_FOODSAVERS -> PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER) && user.isVerified();
            case VERIFIED_FOODSAVERS_HOME_REGION -> PermissionService.isAtLeast(user.getRole(), Role.FOODSAVER)
                    && user.isVerified() && regionId.equals(user.getHomeRegionId());
            case STORE_MANAGERS -> PermissionService.isAtLeast(user.getRole(), Role.STORE_MANAGER);
            case AMBASSADORS -> PermissionService.isAtLeast(user.getRole(), Role.AMBASSADOR);
        };
    }

    private static Set<UUID> ids(Collection<User> list) {
        Set<UUID> result = new LinkedHashSet<>();
        list.forEach(u -> result.add(u.getId()));
        return result;
    }
}
