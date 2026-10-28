package network.foodsharing.event.web;

import network.foodsharing.api.EventsApi;
import network.foodsharing.api.model.Event;
import network.foodsharing.api.model.EventCreate;
import network.foodsharing.api.model.EventInvite;
import network.foodsharing.api.model.EventRsvp;
import network.foodsharing.api.model.EventSummary;
import network.foodsharing.api.model.EventUpdate;
import network.foodsharing.api.model.EventWallPost;
import network.foodsharing.api.model.EventWallPostCreate;
import network.foodsharing.api.model.WallPostCreate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Events (E1-E3). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class EventsController implements EventsApi {

    @Override
    public ResponseEntity<Event> createEvent(EventCreate eventCreate) {
        throw ApiException.notImplemented("createEvent");
    }

    @Override
    public ResponseEntity<EventWallPost> createEventWallPost(UUID eventId, EventWallPostCreate eventWallPostCreate) {
        throw ApiException.notImplemented("createEventWallPost");
    }

    @Override
    public ResponseEntity<Void> deleteEvent(UUID eventId) {
        throw ApiException.notImplemented("deleteEvent");
    }

    @Override
    public ResponseEntity<Void> deleteEventWallPost(UUID eventId, UUID postId) {
        throw ApiException.notImplemented("deleteEventWallPost");
    }

    @Override
    public ResponseEntity<Event> getEvent(UUID eventId) {
        throw ApiException.notImplemented("getEvent");
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getEventIcs(UUID eventId) {
        throw ApiException.notImplemented("getEventIcs");
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> getMyEventsIcs() {
        throw ApiException.notImplemented("getMyEventsIcs");
    }

    @Override
    public ResponseEntity<Void> inviteToEvent(UUID eventId, EventInvite eventInvite) {
        throw ApiException.notImplemented("inviteToEvent");
    }

    @Override
    public ResponseEntity<List<EventSummary>> listMyEvents(OffsetDateTime from) {
        throw ApiException.notImplemented("listMyEvents");
    }

    @Override
    public ResponseEntity<List<EventSummary>> listRegionEvents(UUID regionId, OffsetDateTime from, OffsetDateTime to, Boolean includePast) {
        throw ApiException.notImplemented("listRegionEvents");
    }

    @Override
    public ResponseEntity<Event> respondToEvent(UUID eventId, EventRsvp eventRsvp) {
        throw ApiException.notImplemented("respondToEvent");
    }

    @Override
    public ResponseEntity<Event> updateEvent(UUID eventId, EventUpdate eventUpdate) {
        throw ApiException.notImplemented("updateEvent");
    }
}
