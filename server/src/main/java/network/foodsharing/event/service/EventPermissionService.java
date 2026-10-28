package network.foodsharing.event.service;

import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.repository.EventRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the facts for {@link EventRules} (A36). */
@Service
@Transactional(readOnly = true)
public class EventPermissionService {

    private final PermissionService permissions;
    private final RegionService regions;
    private final EventRepository events;
    private final CurrentActorService currentActor;

    public EventPermissionService(PermissionService permissions, RegionService regions, EventRepository events,
                                  CurrentActorService currentActor) {
        this.permissions = permissions;
        this.regions = regions;
        this.events = events;
        this.currentActor = currentActor;
    }

    /** An event with the current actor's facts. */
    public record EventContext(Event event, EventRules.Actor actor) {

        public boolean mayEdit() {
            return EventRules.mayEdit(actor, event.getAuthorId());
        }
    }

    public EventRules.Actor actor(SessionPrincipal principal, UUID regionId) {
        return new EventRules.Actor(principal.userId(), permissions.isOrga(principal),
                permissions.isMemberOf(principal, regionId), permissions.isAmbassador(principal, regionId));
    }

    /** @throws ApiException 404 REGION_NOT_FOUND */
    public EventRules.Actor currentActor(UUID regionId) {
        regions.require(regionId);
        return actor(currentActor.requirePrincipal(), regionId);
    }

    /** @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED when the actor may not see it */
    public EventContext visibleEvent(UUID eventId) {
        Event event = events.findById(eventId).filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.EVENT_NOT_FOUND));
        EventRules.Actor actor = actor(currentActor.requirePrincipal(), event.getRegionId());
        require(EventRules.maySee(actor, event.isPublicEvent()));
        return new EventContext(event, actor);
    }

    /** @throws ApiException 403 ACCESS_DENIED unless the rule holds */
    public static void require(boolean allowed) {
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
