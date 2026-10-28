package network.foodsharing.forum.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the facts for {@link ForumRules} (A36). Moderators of a region: ORGA, the admins of its MODERATION function
 * group when that group has admins, else the ambassadors of the region or one of its ancestors (A10). A MODERATION
 * group without admins falls back to the ambassadors so a forum never ends up unmoderatable.
 */
@Service
@Transactional(readOnly = true)
public class ForumPermissionService {

    private final PermissionService permissions;
    private final RegionService regions;
    private final RegionReadService regionRead;
    private final RegionOptionsService options;
    private final UserService users;
    private final ForumThreadRepository threads;
    private final CurrentActorService currentActor;

    public ForumPermissionService(PermissionService permissions, RegionService regions, RegionReadService regionRead,
                                  RegionOptionsService options, UserService users, ForumThreadRepository threads,
                                  CurrentActorService currentActor) {
        this.threads = threads;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.regions = regions;
        this.regionRead = regionRead;
        this.options = options;
        this.users = users;
    }

    /** A thread with the current actor's facts. */
    public record ThreadContext(ForumThread thread, ForumRules.Actor actor, ForumRules.Forum forum) {
    }

    /** A forum with the current actor's facts. */
    public record ForumContext(UUID regionId, ForumRules.Actor actor, ForumRules.Forum forum) {
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 FORUM_NOT_ACCESSIBLE */
    public ForumContext accessibleForum(UUID regionId, boolean ambassadorForum) {
        ForumRules.Forum forum = forum(regionId, ambassadorForum);
        ForumRules.Actor actor = actor(currentActor.requirePrincipal(), regionId);
        requireAccess(actor, forum);
        return new ForumContext(regionId, actor, forum);
    }

    /** @throws ApiException 404 THREAD_NOT_FOUND (also when the actor may not see it), 403 FORUM_NOT_ACCESSIBLE */
    public ThreadContext visibleThread(UUID threadId) {
        ForumThread thread = threads.findById(threadId).filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.THREAD_NOT_FOUND));
        ForumRules.Forum forum = forum(thread);
        ForumRules.Actor actor = actor(currentActor.requirePrincipal(), thread.getRegionId());
        requireAccess(actor, forum);
        if (!ForumRules.maySeeThread(actor, forum, thread.isActive(), thread.getAuthorId())) {
            throw ApiException.notFound(ApiErrorCode.THREAD_NOT_FOUND);
        }
        return new ThreadContext(thread, actor, forum);
    }

    /** The users among {@code userIds} who may read the forum (mention bells). */
    public Set<UUID> withAccess(Set<UUID> userIds, UUID regionId, ForumRules.Forum forum) {
        Set<UUID> result = new LinkedHashSet<>();
        for (var user : users.findActive(userIds)) {
            SessionPrincipal principal = new SessionPrincipal(user.getId(), null, user.getRole(), user.getEmail());
            ForumRules.Actor actor = new ForumRules.Actor(user.getId(), permissions.isOrga(principal),
                    permissions.isMemberOf(principal, regionId), permissions.isAmbassador(principal, regionId),
                    false, user.isVerified());
            if (ForumRules.mayAccess(actor, forum)) {
                result.add(user.getId());
            }
        }
        return result;
    }

    /** @throws ApiException 403 ACCESS_DENIED unless the rule holds */
    public static void require(boolean allowed) {
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    public ForumRules.Actor actor(SessionPrincipal principal, UUID regionId) {
        boolean orga = permissions.isOrga(principal);
        boolean verified = users.findActive(principal.userId()).map(u -> u.isVerified()).orElse(false);
        return new ForumRules.Actor(principal.userId(), orga, permissions.isMemberOf(principal, regionId),
                permissions.isAmbassador(principal, regionId), orga || isModerator(principal.userId(), regionId),
                verified);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    public ForumRules.Forum forum(UUID regionId, boolean ambassadorForum) {
        Region region = regions.require(regionId);
        return new ForumRules.Forum(ambassadorForum, region.isModerated(), options.read(regionId).allowHidingInForum());
    }

    public ForumRules.Forum forum(ForumThread thread) {
        return forum(thread.getRegionId(), thread.isAmbassadorForum());
    }

    /** @throws ApiException 403 FORUM_NOT_ACCESSIBLE */
    public void requireAccess(ForumRules.Actor actor, ForumRules.Forum forum) {
        if (!ForumRules.mayAccess(actor, forum)) {
            throw ApiException.forbidden(ApiErrorCode.FORUM_NOT_ACCESSIBLE);
        }
    }

    public boolean isModerator(UUID userId, UUID regionId) {
        return moderators(regionId).contains(userId) || ancestorAmbassador(userId, regionId);
    }

    private boolean ancestorAmbassador(UUID userId, UUID regionId) {
        if (moderationGroupAdmins(regionId).isPresent()) {
            return false;
        }
        return regions.ancestors(regionId).stream().anyMatch(r -> regions.adminIds(r.getId()).contains(userId));
    }

    /**
     * Who gets moderation bells: the MODERATION group admins, else the region's ambassadors, else those of the
     * nearest ancestor that has any (ORGA is not included).
     */
    public Set<UUID> moderators(UUID regionId) {
        Optional<Set<UUID>> groupAdmins = moderationGroupAdmins(regionId);
        if (groupAdmins.isPresent()) {
            return groupAdmins.get();
        }
        List<Region> chain = regions.ancestors(regionId);
        for (int i = chain.size() - 1; i >= 0; i--) {
            Set<UUID> admins = regions.adminIds(chain.get(i).getId());
            if (!admins.isEmpty()) {
                return new LinkedHashSet<>(admins);
            }
        }
        return Set.of();
    }

    private Optional<Set<UUID>> moderationGroupAdmins(UUID regionId) {
        return regionRead.functionGroups(regionId).stream()
                .filter(group -> group.function() == WorkingGroupFunction.MODERATION)
                .map(group -> regions.adminIds(group.group().getId()))
                .filter(admins -> !admins.isEmpty())
                .findFirst();
    }
}
