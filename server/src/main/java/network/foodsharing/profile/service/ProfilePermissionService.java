package network.foodsharing.profile.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.ProfileStoreInfo;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only place profile rights are decided: gathers the facts and applies {@link ProfileRules}. */
@Service
public class ProfilePermissionService {

    /** Viewer, viewed user and the derived rights. */
    public record Context(SessionPrincipal actor, User viewer, User target, ProfileRules.Facts facts,
                          ProfileRules.Permissions permissions) {

        public boolean orga() {
            return facts.orga();
        }

        public boolean administers() {
            return facts.orga() || facts.administers();
        }
    }

    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final UserService users;
    private final ProfileStoreInfo stores;
    private final MembershipService memberships;
    private final RegionOptionsService regionOptions;

    public ProfilePermissionService(CurrentActorService actors, PermissionService permissions, UserService users,
                                    ProfileStoreInfo stores, MembershipService memberships,
                                    RegionOptionsService regionOptions) {
        this.actors = actors;
        this.permissions = permissions;
        this.users = users;
        this.stores = stores;
        this.memberships = memberships;
        this.regionOptions = regionOptions;
    }

    /** @throws ApiException 401, 404 USER_NOT_FOUND (missing or disabled) */
    @Transactional(readOnly = true)
    public Context context(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        User target = users.requireActive(userId);
        User viewer = users.require(actor.userId());
        boolean self = actor.userId().equals(userId);
        boolean orga = permissions.isOrga(actor);
        boolean administers = !self && permissions.mayAdministrateUser(actor, userId);
        boolean shareTeam = !self && stores.shareTeam(actor.userId(), userId);
        boolean manages = !self && stores.managesStoreOf(actor.userId(), userId);
        boolean report = !self && reportButtonInCommonRegion(actor.userId(), userId);
        ProfileRules.Facts facts = new ProfileRules.Facts(self, orga, administers, shareTeam, manages, report);
        return new Context(actor, viewer, target, facts, ProfileRules.permissions(facts));
    }

    /** Lightweight context for the wall and bananas (no store or region lookups). */
    @Transactional(readOnly = true)
    public Context basic(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        User target = users.requireActive(userId);
        User viewer = users.require(actor.userId());
        boolean self = actor.userId().equals(userId);
        boolean orga = permissions.isOrga(actor);
        boolean administers = !self && permissions.mayAdministrateUser(actor, userId);
        ProfileRules.Facts facts = new ProfileRules.Facts(self, orga, administers, false, false, false);
        return new Context(actor, viewer, target, facts, ProfileRules.permissions(facts));
    }

    public static void require(boolean allowed) {
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    private boolean reportButtonInCommonRegion(UUID viewerId, UUID userId) {
        Set<UUID> common = new HashSet<>();
        memberships.units(viewerId).stream().filter(MembershipService.Unit::member)
                .filter(unit -> !unit.region().isGroup()).forEach(unit -> common.add(unit.region().getId()));
        Set<UUID> theirs = new HashSet<>();
        memberships.units(userId).stream().filter(MembershipService.Unit::member)
                .forEach(unit -> theirs.add(unit.region().getId()));
        common.retainAll(theirs);
        return common.stream().anyMatch(regionId -> regionOptions.read(regionId).enableReportButton());
    }
}
