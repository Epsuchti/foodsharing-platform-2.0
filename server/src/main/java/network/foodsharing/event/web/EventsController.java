package network.foodsharing.event.web;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.EventsApi;
import network.foodsharing.api.model.Event;
import network.foodsharing.api.model.EventCreate;
import network.foodsharing.api.model.EventInvite;
import network.foodsharing.api.model.EventRsvp;
import network.foodsharing.api.model.EventSummary;
import network.foodsharing.api.model.EventUpdate;
import network.foodsharing.api.model.EventWallPost;
import network.foodsharing.api.model.EventWallPostCreate;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.event.mapper.EventMapper;
import network.foodsharing.event.service.EventIcsService;
import network.foodsharing.event.service.EventPermissionService;
import network.foodsharing.event.service.EventService;
import network.foodsharing.event.service.EventWallService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Events (E1-E3). */
@RestController
public class EventsController implements EventsApi {

    static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final EventService events;
    private final EventWallService wall;
    private final EventIcsService ics;
    private final EventPermissionService access;
    private final EventMapper mapper;

    public EventsController(EventService events, EventWallService wall, EventIcsService ics,
                            EventPermissionService access, EventMapper mapper) {
        this.events = events;
        this.wall = wall;
        this.ics = ics;
        this.access = access;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<Event> createEvent(EventCreate eventCreate) {
        var details = events.create(eventCreate.getRegionId(), EventMapper.toDraft(eventCreate),
                Boolean.TRUE.equals(eventCreate.getInviteAllMembers()));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toEvent(details));
    }

    @Override
    public ResponseEntity<EventWallPost> createEventWallPost(UUID eventId, EventWallPostCreate eventWallPostCreate) {
        var post = wall.create(eventId, eventWallPostCreate.getBody());
        var context = access.visibleEvent(eventId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toWallPost(post, context.actor(), context.event().getAuthorId()));
    }

    @Override
    public ResponseEntity<Void> deleteEvent(UUID eventId) {
        events.delete(eventId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteEventWallPost(UUID eventId, UUID postId) {
        wall.delete(eventId, postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Event> getEvent(UUID eventId) {
        return ResponseEntity.ok(mapper.toEvent(events.get(eventId)));
    }

    @Override
    public ResponseEntity<Resource> getEventIcs(UUID eventId) {
        return calendar(ics.eventIcs(eventId), "event-" + eventId + ".ics");
    }

    @Override
    public ResponseEntity<Resource> getMyEventsIcs() {
        return calendar(ics.myEventsIcs(), "my-events.ics");
    }

    @Override
    public ResponseEntity<Void> inviteToEvent(UUID eventId, EventInvite eventInvite) {
        events.invite(eventId, eventInvite.getUserIds());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<EventSummary>> listMyEvents(OffsetDateTime from) {
        return ResponseEntity.ok(mapper.toSummaries(events.listMine(TimeMapper.toInstant(from))));
    }

    @Override
    public ResponseEntity<List<EventSummary>> listRegionEvents(UUID regionId, OffsetDateTime from, OffsetDateTime to,
                                                               Boolean includePast) {
        return ResponseEntity.ok(mapper.toSummaries(events.listRegion(regionId, TimeMapper.toInstant(from),
                TimeMapper.toInstant(to), Boolean.TRUE.equals(includePast))));
    }

    @Override
    public ResponseEntity<Event> respondToEvent(UUID eventId, EventRsvp eventRsvp) {
        return ResponseEntity.ok(mapper.toEvent(events.respond(eventId, EventMapper.toDomain(eventRsvp.getStatus()))));
    }

    @Override
    public ResponseEntity<Event> updateEvent(UUID eventId, EventUpdate eventUpdate) {
        return ResponseEntity.ok(mapper.toEvent(events.update(eventId, EventMapper.toDraft(eventUpdate))));
    }

    private static ResponseEntity<Resource> calendar(String body, String filename) {
        return ResponseEntity.ok().contentType(CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build()
                        .toString())
                .body(new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)));
    }
}
