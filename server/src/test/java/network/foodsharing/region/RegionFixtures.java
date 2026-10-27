package network.foodsharing.region;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.domain.ApplyType;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionJoinMode;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.domain.RegionPermission;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.Permission;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;

/** Builds region trees, users and memberships in slice tests (closure rows included). */
public final class RegionFixtures {

    private final RegionRepository regions;
    private final RegionClosureRepository closure;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionFunctionRepository functions;
    private final RegionPermissionRepository permissions;
    private final UserRepository users;
    private int counter;

    public RegionFixtures(RegionRepository regions, RegionClosureRepository closure,
                          RegionMembershipRepository memberships, RegionAdminRepository admins,
                          RegionFunctionRepository functions, RegionPermissionRepository permissions,
                          UserRepository users) {
        this.regions = regions;
        this.closure = closure;
        this.memberships = memberships;
        this.admins = admins;
        this.functions = functions;
        this.permissions = permissions;
        this.users = users;
    }

    public Region region(String slug, UnitType type, Region parent) {
        Region region = new Region();
        region.setSlug(slug);
        region.setName(slug);
        region.setType(type);
        region.setParentId(parent == null ? null : parent.getId());
        region.setJoinMode(RegionJoinMode.OPEN);
        if (type == UnitType.WORKING_GROUP) {
            region.setApplyType(ApplyType.EVERYBODY);
        }
        Region saved = regions.saveAndFlush(region);
        closure.insertLeaf(saved.getId(), saved.getParentId());
        if (parent != null) {
            parent.setHasChildren(true);
        }
        return saved;
    }

    public Region group(String slug, Region parent, WorkingGroupFunction function) {
        Region group = region(slug, UnitType.WORKING_GROUP, parent);
        if (function != null) {
            RegionFunction assignment = new RegionFunction();
            assignment.setRegionId(parent.getId());
            assignment.setFunction(function);
            assignment.setGroupId(group.getId());
            functions.saveAndFlush(assignment);
        }
        return group;
    }

    public User user(String name, Role role) {
        counter++;
        return users.saveAndFlush(TestUsers.user(name + counter + "@test.local", role));
    }

    public User user(String name) {
        return user(name, Role.FOODSAVER);
    }

    public RegionMembership member(User user, Region region) {
        RegionMembership membership = new RegionMembership();
        membership.setUserId(user.getId());
        membership.setRegionId(region.getId());
        membership.setActive(true);
        membership.setJoinedAt(FixedClockConfig.SEED_NOW.minusSeconds(86_400L * (++counter)));
        return memberships.saveAndFlush(membership);
    }

    public void admin(User user, Region region) {
        RegionAdmin admin = new RegionAdmin();
        admin.setUserId(user.getId());
        admin.setRegionId(region.getId());
        admins.saveAndFlush(admin);
    }

    public void grant(User user, Region region, Permission permission) {
        RegionPermission grant = new RegionPermission();
        grant.setUserId(user.getId());
        grant.setRegionId(region.getId());
        grant.setPermission(permission);
        permissions.saveAndFlush(grant);
    }

    public static Instant now() {
        return FixedClockConfig.SEED_NOW;
    }

    public static UUID unknownId() {
        return UUID.fromString("00000000-0000-4000-8000-00000000dead");
    }
}
