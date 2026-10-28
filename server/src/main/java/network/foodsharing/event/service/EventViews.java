package network.foodsharing.event.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventInvitation;
import network.foodsharing.event.domain.EventLocation;
import network.foodsharing.event.domain.EventType;
import network.foodsharing.event.domain.EventWallPost;

/** Read models and inputs of the event services. */
public final class EventViews {

    private EventViews() {
    }

    /** Location input; lat/lon geocoded from the postcode when absent. */
    public record LocationDraft(String name, String street, String zip, String city, Double lat, Double lon) {
    }

    /** Create / update input; on update null = unchanged ({@code onlineLink ""} clears it). */
    public record EventDraft(EventType type, String name, String description, Instant startsAt, Instant endsAt,
                             Boolean publicEvent, LocationDraft location, String onlineLink) {
    }

    /** An event in a listing with its invitations (counts, my status). */
    public record EventItem(Event event, EventLocation location, List<EventInvitation> invitations, UUID viewerId) {
    }

    /** One event with everything the detail page shows. */
    public record EventDetails(EventItem item, List<EventWallPost> wallPosts, EventRules.Actor actor, Instant now) {
    }
}
