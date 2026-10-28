package network.foodsharing.event.service;

import java.util.UUID;
import network.foodsharing.event.repository.EventRepository;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** EVENT history: readable by the members of the event's region (and ORGA, which the history module adds). */
@Component
public class EventHistoryAccessRule implements HistoryAccessRule {

    private final EventRepository events;
    private final EventPermissionService access;

    public EventHistoryAccessRule(EventRepository events, EventPermissionService access) {
        this.events = events;
        this.access = access;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.EVENT;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return events.findById(subjectId).map(event -> {
            EventRules.Actor facts = access.actor(actor, event.getRegionId());
            return facts.member() || EventRules.mayEdit(facts, event.getAuthorId());
        }).orElse(false);
    }
}
