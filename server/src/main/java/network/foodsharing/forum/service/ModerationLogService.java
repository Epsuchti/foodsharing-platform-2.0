package network.foodsharing.forum.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.forum.domain.ForumAction;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderation log of a region's forums (A37, D10.3): moderation actions on THREAD subjects, moderators only. */
@Service
@Transactional(readOnly = true)
public class ModerationLogService {

    /** Actions shown in the moderation log (A37). */
    public static final List<String> ACTIONS = List.of(ForumAction.THREAD_ACTIVATED, ForumAction.THREAD_CLOSED,
            ForumAction.THREAD_OPENED, ForumAction.THREAD_STICKY_CHANGED, ForumAction.THREAD_DELETED,
            ForumAction.POST_HIDDEN, ForumAction.POST_UNHIDDEN, ForumAction.POST_DELETED).stream()
            .map(ForumAction::code).toList();

    private final ForumPermissionService access;
    private final HistoryQueryService historyQuery;
    private final CurrentActorService currentActor;

    public ModerationLogService(ForumPermissionService access, HistoryQueryService historyQuery,
                                CurrentActorService currentActor) {
        this.access = access;
        this.historyQuery = historyQuery;
        this.currentActor = currentActor;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED unless moderator of the region (or ORGA) */
    public Page<HistoryEntry> log(UUID regionId, Instant from, Instant to, int page, int size) {
        ForumRules.Forum forum = access.forum(regionId, false);
        ForumRules.Actor actor = access.actor(currentActor.requirePrincipal(), regionId);
        if (!(actor.orga() || actor.moderator()) || forum == null) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return historyQuery.listForRegion(SubjectType.THREAD, regionId, ACTIONS, from, to, page, size);
    }
}
