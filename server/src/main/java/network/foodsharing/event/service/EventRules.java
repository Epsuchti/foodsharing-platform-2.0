package network.foodsharing.event.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Event permission rules (A36, upstream {@code EventPermissions}) as pure functions. Facts are resolved by
 * {@link EventPermissionService}.
 */
public final class EventRules {

    /** RSVPs stay open until this long after the end (upstream "24 h after the event end"). */
    public static final Duration RSVP_GRACE = Duration.ofHours(24);

    /** The acting user's facts for one event region. */
    public record Actor(UUID userId, boolean orga, boolean member, boolean regionAdmin) {
    }

    private EventRules() {
    }

    /** see = public event or region member (ORGA sees everything). */
    public static boolean maySee(Actor actor, boolean publicEvent) {
        return publicEvent || actor.member() || actor.orga();
    }

    /** create = region member. */
    public static boolean mayCreate(Actor actor) {
        return actor.member();
    }

    /** edit / delete / invite = ORGA, region admin, author. */
    public static boolean mayEdit(Actor actor, UUID authorId) {
        return actor.orga() || actor.regionAdmin() || actor.userId().equals(authorId);
    }

    /** RSVP = may see and {@code now ≤ endsAt + 24 h}. */
    public static boolean mayRsvp(Actor actor, boolean publicEvent, Instant endsAt, Instant now) {
        return maySee(actor, publicEvent) && rsvpOpen(endsAt, now);
    }

    public static boolean rsvpOpen(Instant endsAt, Instant now) {
        return !now.isAfter(endsAt.plus(RSVP_GRACE));
    }

    /** wall comment = may see. */
    public static boolean mayComment(Actor actor, boolean publicEvent) {
        return maySee(actor, publicEvent);
    }

    /** delete a wall comment = its author or an event editor. */
    public static boolean mayDeleteWallPost(Actor actor, UUID eventAuthorId, UUID postAuthorId) {
        return actor.userId().equals(postAuthorId) || mayEdit(actor, eventAuthorId);
    }
}
