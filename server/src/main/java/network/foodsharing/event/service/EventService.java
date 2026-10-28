package network.foodsharing.event.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventAction;
import network.foodsharing.event.domain.EventInvitation;
import network.foodsharing.event.domain.EventLocation;
import network.foodsharing.event.domain.EventType;
import network.foodsharing.event.domain.InvitationStatus;
import network.foodsharing.event.repository.EventInvitationRepository;
import network.foodsharing.event.repository.EventLocationRepository;
import network.foodsharing.event.repository.EventRepository;
import network.foodsharing.event.repository.EventWallPostRepository;
import network.foodsharing.event.service.EventPermissionService.EventContext;
import network.foodsharing.event.service.EventViews.EventDetails;
import network.foodsharing.event.service.EventViews.EventDraft;
import network.foodsharing.event.service.EventViews.EventItem;
import network.foodsharing.event.service.EventViews.LocationDraft;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Events (E1, E2): listing, create/update/delete, invitations, RSVP (A36). */
@Service
public class EventService {

    private static final Logger LOG = LoggerFactory.getLogger(EventService.class);
    static final Set<InvitationStatus> ATTENDING = EnumSet.of(InvitationStatus.ACCEPTED, InvitationStatus.MAYBE);

    private final EventRepository events;
    private final EventLocationRepository locations;
    private final EventInvitationRepository invitations;
    private final EventWallPostRepository wallPosts;
    private final EventPermissionService access;
    private final EventNotifier notifier;
    private final RegionService regions;
    private final GeocodingClient geocoding;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final Clock clock;

    public EventService(EventRepository events, EventLocationRepository locations,
                        EventInvitationRepository invitations, EventWallPostRepository wallPosts,
                        EventPermissionService access, EventNotifier notifier, RegionService regions,
                        GeocodingClient geocoding, UserService users, CurrentActorService currentActor,
                        HistoryService history, Clock clock) {
        this.events = events;
        this.locations = locations;
        this.invitations = invitations;
        this.wallPosts = wallPosts;
        this.access = access;
        this.notifier = notifier;
        this.regions = regions;
        this.geocoding = geocoding;
        this.users = users;
        this.currentActor = currentActor;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Events of a region: upcoming (not yet ended) unless {@code includePast}; {@code from}/{@code to} bound the
     * start. Non-members see public events only.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 400 VALIDATION_FAILED when {@code to < from}
     */
    @Transactional(readOnly = true)
    public List<EventItem> listRegion(UUID regionId, Instant from, Instant to, boolean includePast) {
        EventRules.Actor actor = access.currentActor(regionId);
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Instant endsAfter = includePast ? Instant.EPOCH : clock.instant();
        if (from != null && from.isAfter(endsAfter)) {
            endsAfter = from;
        }
        Instant startsBefore = to == null ? Instant.parse("9999-01-01T00:00:00Z") : to;
        boolean publicOnly = !(actor.member() || actor.orga());
        return items(events.listRegion(regionId, publicOnly, endsAfter, startsBefore), actor.userId());
    }

    /** Events I am invited to or attend that end at or after {@code from} (default now). */
    @Transactional(readOnly = true)
    public List<EventItem> listMine(Instant from) {
        UUID me = currentActor.requireUserId();
        return items(events.listInvited(me, EnumSet.allOf(InvitationStatus.class),
                from == null ? clock.instant() : from), me);
    }

    /** My accepted / maybe events of the last 90 days and the future (ICS feed). */
    @Transactional(readOnly = true)
    public List<EventItem> listMineAttending() {
        UUID me = currentActor.requireUserId();
        return items(events.listInvited(me, ATTENDING, clock.instant().minus(90, ChronoUnit.DAYS)), me);
    }

    /** @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public EventDetails get(UUID eventId) {
        return details(access.visibleEvent(eventId));
    }

    /** @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public EventItem item(UUID eventId) {
        EventContext context = access.visibleEvent(eventId);
        return items(List.of(context.event()), context.actor().userId()).getFirst();
    }

    /**
     * Creates an event; the author attends (ACCEPTED). With {@code inviteAllMembers} every other active member of the
     * region gets an INVITED row and an {@code EVENT_INVITATION} bell.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED, 400 EVENT_DATES_INVALID / LOCATION_REQUIRED /
     *                      ONLINE_LINK_REQUIRED
     */
    @Transactional
    public EventDetails create(UUID regionId, EventDraft draft, boolean inviteAllMembers) {
        EventRules.Actor actor = access.currentActor(regionId);
        EventPermissionService.require(EventRules.mayCreate(actor));
        Event event = new Event();
        event.setRegionId(regionId);
        event.setAuthorId(actor.userId());
        event.setType(draft.type());
        event.setName(draft.name().strip());
        event.setDescription(blankToNull(draft.description()));
        event.setStartsAt(draft.startsAt());
        event.setEndsAt(draft.endsAt());
        event.setPublicEvent(Boolean.TRUE.equals(draft.publicEvent()));
        event.setOnlineLink(blankToNull(draft.onlineLink()));
        EventLocation location = draft.location() == null ? null : apply(new EventLocation(), draft.location());
        validate(event, location);
        if (location != null) {
            event.setLocationId(locations.save(location).getId());
        }
        event = events.save(event);
        EventInvitation own = new EventInvitation();
        own.setEventId(event.getId());
        own.setUserId(actor.userId());
        own.setStatus(InvitationStatus.ACCEPTED);
        own.setRespondedAt(clock.instant());
        invitations.save(own);
        history.record(event(EventAction.CREATED, event).metadata("type", event.getType().name())
                .metadata("public", event.isPublicEvent()).build());
        if (inviteAllMembers) {
            invite(event, new LinkedHashSet<>(regions.activeMemberIds(regionId)));
        }
        return details(new EventContext(event, actor));
    }

    /**
     * Partial update; participants (ACCEPTED / MAYBE, except the editor) get {@code EVENT_UPDATED}.
     *
     * @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED, 400 EVENT_DATES_INVALID / LOCATION_REQUIRED /
     *                      ONLINE_LINK_REQUIRED
     */
    @Transactional
    public EventDetails update(UUID eventId, EventDraft draft) {
        EventContext context = access.visibleEvent(eventId);
        EventPermissionService.require(context.mayEdit());
        Event event = context.event();
        HistoryEvent.Builder entry = event(EventAction.UPDATED, event);
        boolean changed = false;
        if (draft.type() != null && draft.type() != event.getType()) {
            entry.metadata("type", draft.type().name());
            event.setType(draft.type());
            changed = true;
        }
        if (draft.name() != null && !draft.name().strip().equals(event.getName())) {
            entry.change("name", event.getName(), draft.name().strip());
            event.setName(draft.name().strip());
            changed = true;
        }
        if (draft.description() != null && !Objects.equals(blankToNull(draft.description()), event.getDescription())) {
            event.setDescription(blankToNull(draft.description()));
            entry.metadata("description", true);
            changed = true;
        }
        if (draft.startsAt() != null && !draft.startsAt().equals(event.getStartsAt())) {
            entry.metadata("startsAt", draft.startsAt().toString());
            event.setStartsAt(draft.startsAt());
            changed = true;
        }
        if (draft.endsAt() != null && !draft.endsAt().equals(event.getEndsAt())) {
            entry.metadata("endsAt", draft.endsAt().toString());
            event.setEndsAt(draft.endsAt());
            changed = true;
        }
        if (draft.publicEvent() != null && draft.publicEvent() != event.isPublicEvent()) {
            entry.metadata("public", draft.publicEvent());
            event.setPublicEvent(draft.publicEvent());
            changed = true;
        }
        if (draft.onlineLink() != null && !Objects.equals(blankToNull(draft.onlineLink()), event.getOnlineLink())) {
            event.setOnlineLink(blankToNull(draft.onlineLink()));
            entry.metadata("onlineLink", true);
            changed = true;
        }
        EventLocation location = event.getLocationId() == null ? null
                : locations.findById(event.getLocationId()).orElse(null);
        if (draft.location() != null) {
            location = apply(location == null ? new EventLocation() : location, draft.location());
            entry.metadata("location", true);
            changed = true;
        }
        validate(event, location);
        if (!changed) {
            return details(context);
        }
        if (location != null) {
            event.setLocationId(locations.save(location).getId());
        }
        events.save(event);
        history.record(entry.build());
        Set<UUID> participants = attendees(event.getId());
        participants.remove(context.actor().userId());
        notifier.updated(event, participants);
        return details(context);
    }

    /** Soft delete; its bells are removed. @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID eventId) {
        EventContext context = access.visibleEvent(eventId);
        EventPermissionService.require(context.mayEdit());
        Event event = context.event();
        event.setDeletedAt(clock.instant());
        events.save(event);
        history.record(event(EventAction.DELETED, event).metadata("name", event.getName()).build());
        notifier.removed(eventId);
    }

    /**
     * Invites users (existing invitations are kept). Private events: invitees must be members of the region.
     *
     * @throws ApiException 404 EVENT_NOT_FOUND / USER_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_FAILED
     */
    @Transactional
    public void invite(UUID eventId, Collection<UUID> userIds) {
        EventContext context = access.visibleEvent(eventId);
        EventPermissionService.require(context.mayEdit());
        Set<UUID> wanted = new LinkedHashSet<>(userIds);
        List<User> found = users.findActive(wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.notFound(ApiErrorCode.USER_NOT_FOUND);
        }
        Event event = context.event();
        if (!event.isPublicEvent()) {
            Set<UUID> members = new LinkedHashSet<>(regions.activeMemberIds(event.getRegionId()));
            if (!members.containsAll(wanted)) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
        }
        invite(event, wanted);
    }

    private void invite(Event event, Set<UUID> userIds) {
        Set<UUID> existing = invitations.findByEventIdOrderByCreatedAt(event.getId()).stream()
                .map(EventInvitation::getUserId).collect(Collectors.toSet());
        List<UUID> invited = new ArrayList<>();
        for (UUID userId : userIds) {
            if (existing.contains(userId)) {
                continue;
            }
            EventInvitation invitation = new EventInvitation();
            invitation.setEventId(event.getId());
            invitation.setUserId(userId);
            invitation.setStatus(InvitationStatus.INVITED);
            invitations.save(invitation);
            invited.add(userId);
        }
        if (invited.isEmpty()) {
            return;
        }
        history.record(event(EventAction.INVITATIONS_SENT, event).metadata("count", invited.size()).build());
        notifier.invited(event, invited);
    }

    /**
     * Accept, maybe or decline until {@code ends_at + 24 h}.
     *
     * @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_FAILED (status INVITED),
     *                      409 EVENT_RSVP_CLOSED
     */
    @Transactional
    public EventDetails respond(UUID eventId, InvitationStatus status) {
        EventContext context = access.visibleEvent(eventId);
        if (status == null || status == InvitationStatus.INVITED) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Event event = context.event();
        Instant now = clock.instant();
        if (!EventRules.rsvpOpen(event.getEndsAt(), now)) {
            throw ApiException.conflict(ApiErrorCode.EVENT_RSVP_CLOSED);
        }
        UUID me = context.actor().userId();
        EventInvitation invitation = invitations.findByEventIdAndUserId(eventId, me).orElseGet(() -> {
            EventInvitation created = new EventInvitation();
            created.setEventId(eventId);
            created.setUserId(me);
            return created;
        });
        InvitationStatus old = invitation.getStatus();
        if (old != status) {
            invitation.setStatus(status);
            invitation.setRespondedAt(now);
            invitations.save(invitation);
            history.record(event(EventAction.RSVP_CHANGED, event).change("status", old.name(), status.name()).build());
        }
        return details(context);
    }

    /** ACCEPTED / MAYBE invitees. */
    Set<UUID> attendees(UUID eventId) {
        return invitations.findByEventIdOrderByCreatedAt(eventId).stream()
                .filter(i -> ATTENDING.contains(i.getStatus())).map(EventInvitation::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    EventDetails details(EventContext context) {
        Event event = context.event();
        EventItem item = items(List.of(event), context.actor().userId()).getFirst();
        return new EventDetails(item, wallPosts.findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(event.getId()),
                context.actor(), clock.instant());
    }

    private List<EventItem> items(List<Event> list, UUID viewerId) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<EventInvitation>> byEvent = invitations.findByEventIdIn(list.stream().map(Event::getId).toList())
                .stream().collect(Collectors.groupingBy(EventInvitation::getEventId));
        Set<UUID> locationIds = list.stream().map(Event::getLocationId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, EventLocation> locationById = locations.findAllById(locationIds).stream()
                .collect(Collectors.toMap(EventLocation::getId, l -> l));
        return list.stream().map(e -> new EventItem(e, e.getLocationId() == null ? null
                : locationById.get(e.getLocationId()), byEvent.getOrDefault(e.getId(), List.of()), viewerId)).toList();
    }

    /** Dates, OFFLINE location, ONLINE https link. */
    static void validate(Event event, EventLocation location) {
        if (event.getStartsAt() == null || event.getEndsAt() == null || !event.getStartsAt().isBefore(event.getEndsAt())) {
            throw ApiException.badRequest(ApiErrorCode.EVENT_DATES_INVALID);
        }
        if (event.getType() == EventType.OFFLINE && (location == null || location.getName() == null
                || location.getName().isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.LOCATION_REQUIRED);
        }
        String link = event.getOnlineLink();
        if (link != null && (!link.startsWith("https://") || link.length() < 9)) {
            throw ApiException.badRequest(ApiErrorCode.ONLINE_LINK_REQUIRED);
        }
        if (event.getType() == EventType.ONLINE && link == null) {
            throw ApiException.badRequest(ApiErrorCode.ONLINE_LINK_REQUIRED);
        }
    }

    private EventLocation apply(EventLocation location, LocationDraft draft) {
        location.setName(draft.name() == null ? null : draft.name().strip());
        location.setStreet(blankToNull(draft.street()));
        location.setZip(blankToNull(draft.zip()));
        location.setCity(blankToNull(draft.city()));
        location.setLat(draft.lat());
        location.setLon(draft.lon());
        if ((draft.lat() == null || draft.lon() == null) && location.getZip() != null) {
            try {
                geocoding.geocodePostcode("CH", location.getZip()).ifPresent(point -> {
                    location.setLat(point.latitude());
                    location.setLon(point.longitude());
                });
            } catch (GeocodingClient.GeocodingUnavailableException e) {
                LOG.info("Event location not geocoded: {}", e.getMessage());
            }
        }
        return location;
    }

    private HistoryEvent.Builder event(EventAction action, Event event) {
        return history.event(action, event.getId()).region(event.getRegionId());
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
