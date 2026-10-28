package network.foodsharing.poll.service;

import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the facts for {@link PollRules} (A36). */
@Service
@Transactional(readOnly = true)
public class PollPermissionService {

    private final PermissionService permissions;
    private final RegionService regions;
    private final RegionReadService regionRead;
    private final UserService users;
    private final PollRepository polls;
    private final CurrentActorService currentActor;

    public PollPermissionService(PermissionService permissions, RegionService regions, RegionReadService regionRead,
                                 UserService users, PollRepository polls, CurrentActorService currentActor) {
        this.permissions = permissions;
        this.regions = regions;
        this.regionRead = regionRead;
        this.users = users;
        this.polls = polls;
        this.currentActor = currentActor;
    }

    /** A poll with the current actor's facts. */
    public record PollContext(Poll poll, PollRules.Actor actor) {
    }

    public PollRules.Actor actor(SessionPrincipal principal, Region region) {
        UUID userId = principal.userId();
        boolean verified = users.findActive(userId).map(u -> u.isVerified()).orElse(false);
        boolean votingAdmin = !region.getType().isGroup() && regionRead.functionGroups(region.getId()).stream()
                .filter(group -> group.function() == WorkingGroupFunction.VOTING)
                .anyMatch(group -> regions.adminIds(group.group().getId()).contains(userId));
        return new PollRules.Actor(userId, permissions.isOrga(principal), permissions.isMemberOf(principal,
                region.getId()), verified, region.getType().isGroup(), permissions.isAmbassador(principal,
                region.getId()), votingAdmin);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    public PollRules.Actor currentActor(UUID regionId) {
        return actor(currentActor.requirePrincipal(), regions.require(regionId));
    }

    /** @throws ApiException 404 POLL_NOT_FOUND, 403 ACCESS_DENIED for non-members */
    public PollContext visiblePoll(UUID pollId) {
        Poll poll = polls.findById(pollId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.POLL_NOT_FOUND));
        return context(poll);
    }

    /** Same as {@link #visiblePoll} but locks the poll row; joins the caller's (writing) transaction (votes). */
    public PollContext visiblePollForUpdate(UUID pollId) {
        Poll poll = polls.findForUpdate(pollId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.POLL_NOT_FOUND));
        return context(poll);
    }

    private PollContext context(Poll poll) {
        PollRules.Actor actor = actor(currentActor.requirePrincipal(), regions.require(poll.getRegionId()));
        require(PollRules.maySee(actor));
        return new PollContext(poll, actor);
    }

    /** @throws ApiException 403 ACCESS_DENIED unless the rule holds */
    public static void require(boolean allowed) {
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
