package network.foodsharing.region.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.RegionFunction;
import network.foodsharing.region.domain.RegionMembership;
import network.foodsharing.region.domain.RegionPermission;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionFunctionRepository;
import network.foodsharing.region.repository.RegionMembershipRepository;
import network.foodsharing.region.repository.RegionPermissionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member management of a region (plan §13.2 R8, AD1): member list with filters, removal with a required reason,
 * ambassadors (ORGA only, upstream {@code maySetRegionAdmin}) and the region permission overview / grants (A10).
 */
@Service
public class RegionMembersService {

    public record MemberView(User user, RegionMembership membership, boolean admin, Set<Permission> permissions,
                             boolean homeRegion, boolean showPhone) {
    }

    public record GrantView(User user, Permission permission, String source, User grantedBy, Instant grantedAt) {
    }

    /** Published after a removal (bell to the removed user after commit). */
    public record MemberRemoved(UUID regionId, UUID userId, String reason) {
    }

    private final RegionService regionService;
    private final RegionMembershipRepository memberships;
    private final RegionAdminRepository admins;
    private final RegionPermissionRepository grants;
    private final RegionFunctionRepository functions;
    private final RegionPermissionResolver resolver;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    public RegionMembersService(RegionService regionService, RegionMembershipRepository memberships,
                                RegionAdminRepository admins, RegionPermissionRepository grants,
                                RegionFunctionRepository functions, RegionPermissionResolver resolver,
                                UserService users, CurrentActorService currentActor, PermissionService permissions,
                                HistoryService history, ApplicationEventPublisher events, AppProperties properties) {
        this.regionService = regionService;
        this.memberships = memberships;
        this.admins = admins;
        this.grants = grants;
        this.functions = functions;
        this.resolver = resolver;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
        this.properties = properties;
    }

    /**
     * Members of a region, paginated (members of the region and its administrators may read it).
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public Page<MemberView> members(UUID regionId, String search, Boolean verified, Role role, Boolean admin,
                                    int page, int size) {
        regionService.require(regionId);
        requireMemberOrAdministrator(regionId);
        String pattern = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
        Page<RegionMembership> result = memberships.searchMembers(regionId, pattern, verified, role, admin,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<UUID, User> byId = users.findActive(result.getContent().stream().map(RegionMembership::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> adminIds = admins.findUserIds(regionId);
        return result.map(membership -> {
            User user = byId.get(membership.getUserId());
            return new MemberView(user, membership, adminIds.contains(user.getId()),
                    resolver.permissionsByRegion(user.getId()).getOrDefault(regionId, Set.of()),
                    regionId.equals(user.getHomeRegionId()), users.mayCurrentUserSeeContact(user));
        });
    }

    /**
     * Removes a member (required reason; the user gets a bell). Ambassadors must first lose their admin role.
     *
     * @throws ApiException 403 unless mayAdministrateRegion, 404 REGION_NOT_FOUND, 409 NOT_A_MEMBER /
     *                      CANNOT_REMOVE_AMBASSADOR
     */
    @Transactional
    public void removeMember(UUID regionId, UUID userId, String reason) {
        Region region = regionService.require(regionId);
        permissions.requireMayAdministrateRegion(currentActor.requirePrincipal(), regionId);
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, regionId)
                .filter(RegionMembership::isActive)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_A_MEMBER));
        if (admins.existsByUserIdAndRegionId(userId, regionId)) {
            throw ApiException.conflict(ApiErrorCode.CANNOT_REMOVE_AMBASSADOR);
        }
        memberships.delete(membership);
        history.record(history.event(RegionAction.MEMBER_REMOVED, regionId).secondarySubject(userId)
                .region(region.isGroup() ? region.getParentId() : regionId).reason(reason).build());
        users.findActive(userId).filter(user -> regionId.equals(user.getHomeRegionId()))
                .ifPresent(user -> users.changeHomeRegion(userId, null));
        events.publishEvent(new MemberRemoved(regionId, userId, reason));
    }

    /**
     * ORGA makes a member ambassador (upstream {@code maySetRegionAdmin}).
     *
     * @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND, 409 NOT_A_MEMBER
     */
    @Transactional
    public MemberView setAdmin(UUID regionId, UUID userId) {
        regionService.require(regionId);
        permissions.requireOrga(currentActor.requirePrincipal());
        RegionMembership membership = memberships.findByUserIdAndRegionId(userId, regionId)
                .filter(RegionMembership::isActive)
                .orElseThrow(() -> ApiException.conflict(ApiErrorCode.NOT_A_MEMBER));
        if (!admins.existsByUserIdAndRegionId(userId, regionId)) {
            RegionAdmin admin = new RegionAdmin();
            admin.setUserId(userId);
            admin.setRegionId(regionId);
            admins.save(admin);
            history.record(history.event(RegionAction.ADMIN_GRANTED, regionId).secondarySubject(userId)
                    .region(regionId).build());
        }
        User user = users.require(userId);
        return new MemberView(user, membership, true, resolver.permissionsByRegion(userId).getOrDefault(regionId,
                Set.of()), regionId.equals(user.getHomeRegionId()), false);
    }

    /** @throws ApiException 403 unless ORGA, 404 REGION_NOT_FOUND */
    @Transactional
    public void removeAdmin(UUID regionId, UUID userId) {
        regionService.require(regionId);
        permissions.requireOrga(currentActor.requirePrincipal());
        admins.findByUserIdAndRegionId(userId, regionId).ifPresent(admin -> {
            admins.delete(admin);
            history.record(history.event(RegionAction.ADMIN_REVOKED, regionId).secondarySubject(userId)
                    .region(regionId).build());
        });
    }

    /**
     * Who holds which region permission, directly or through a working-group function.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED
     */
    @Transactional(readOnly = true)
    public List<GrantView> grants(UUID regionId) {
        regionService.require(regionId);
        requireMemberOrAdministrator(regionId);
        List<GrantView> result = new ArrayList<>();
        List<RegionPermission> direct = grants.findByRegionId(regionId);
        Map<UUID, User> byId = users.findActive(direct.stream().flatMap(g -> java.util.stream.Stream.of(
                        g.getUserId(), g.getGrantedBy())).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        for (RegionPermission grant : direct) {
            if (byId.containsKey(grant.getUserId())) {
                result.add(new GrantView(byId.get(grant.getUserId()), grant.getPermission(), "DIRECT",
                        grant.getGrantedBy() == null ? null : byId.get(grant.getGrantedBy()), grant.getCreatedAt()));
            }
        }
        Map<WorkingGroupFunction, List<Permission>> functionGrants = properties.getPermissions().getFunctionGrants();
        for (RegionFunction assignment : functions.findByRegionId(regionId)) {
            List<Permission> granted = functionGrants.getOrDefault(assignment.getFunction(), List.of());
            if (granted.isEmpty()) {
                continue;
            }
            for (User member : users.findActive(memberships.findActiveUserIds(assignment.getGroupId()))) {
                for (Permission permission : granted) {
                    result.add(new GrantView(member, permission, "FUNCTION:" + assignment.getFunction().name(), null,
                            null));
                }
            }
        }
        result.sort(Comparator.comparing((GrantView g) -> g.user().getDisplayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(GrantView::permission).thenComparing(GrantView::source));
        return result;
    }

    /**
     * Grants a region permission: administrators of the region; CAN_MANAGE_USERS holders only CAN_GIVE_INTRO_PICKUPS.
     *
     * @throws ApiException 403 ACCESS_DENIED, 404 REGION_NOT_FOUND / USER_NOT_FOUND, 409 NOT_A_MEMBER
     */
    @Transactional
    public GrantView grant(UUID regionId, UUID userId, Permission permission) {
        regionService.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        requireMayGrant(actor, regionId, permission);
        User user = users.requireActive(userId);
        if (!memberships.existsByUserIdAndRegionIdAndActiveTrue(userId, regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_A_MEMBER);
        }
        RegionPermission grant = grants.findByUserIdAndRegionIdAndPermission(userId, regionId, permission)
                .orElseGet(() -> {
                    RegionPermission created = new RegionPermission();
                    created.setUserId(userId);
                    created.setRegionId(regionId);
                    created.setPermission(permission);
                    created.setGrantedBy(actor.userId());
                    history.record(history.event(RegionAction.PERMISSION_GRANTED, regionId).secondarySubject(userId)
                            .region(regionId).metadata("permission", permission.name()).build());
                    return grants.save(created);
                });
        return new GrantView(user, permission, "DIRECT",
                grant.getGrantedBy() == null ? null : users.findActive(grant.getGrantedBy()).orElse(null),
                grant.getCreatedAt());
    }

    /** @throws ApiException 403 ACCESS_DENIED, 404 REGION_NOT_FOUND */
    @Transactional
    public void revoke(UUID regionId, UUID userId, Permission permission) {
        regionService.require(regionId);
        requireMayGrant(currentActor.requirePrincipal(), regionId, permission);
        grants.findByUserIdAndRegionIdAndPermission(userId, regionId, permission).ifPresent(grant -> {
            grants.delete(grant);
            history.record(history.event(RegionAction.PERMISSION_REVOKED, regionId).secondarySubject(userId)
                    .region(regionId).metadata("permission", permission.name()).build());
        });
    }

    /** May the actor grant / revoke this permission in the region? */
    public boolean mayGrant(SessionPrincipal actor, UUID regionId, Permission permission) {
        return permissions.mayAdministrateRegion(actor, regionId)
                || (permission == Permission.CAN_GIVE_INTRO_PICKUPS
                && permissions.has(actor, Permission.CAN_MANAGE_USERS, regionId));
    }

    private void requireMayGrant(SessionPrincipal actor, UUID regionId, Permission permission) {
        if (!mayGrant(actor, regionId, permission)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private void requireMemberOrAdministrator(UUID regionId) {
        SessionPrincipal principal = currentActor.requirePrincipal();
        if (!permissions.isMemberOf(principal, regionId)) {
            permissions.requireMayAdministrateRegion(principal, regionId);
        }
    }
}
