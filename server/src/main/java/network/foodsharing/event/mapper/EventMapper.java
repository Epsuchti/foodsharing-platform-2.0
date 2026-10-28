package network.foodsharing.event.mapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Event;
import network.foodsharing.api.model.EventCreate;
import network.foodsharing.api.model.EventLocation;
import network.foodsharing.api.model.EventParticipants;
import network.foodsharing.api.model.EventPermissions;
import network.foodsharing.api.model.EventSummary;
import network.foodsharing.api.model.EventType;
import network.foodsharing.api.model.EventUpdate;
import network.foodsharing.api.model.EventWallPost;
import network.foodsharing.api.model.InvitationStatus;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.event.domain.EventInvitation;
import network.foodsharing.event.service.EventRules;
import network.foodsharing.event.service.EventViews;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Event entities and API models. */
@Component
public class EventMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public EventMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public static network.foodsharing.event.domain.EventType toDomain(EventType type) {
        return type == null ? null : network.foodsharing.event.domain.EventType.valueOf(type.getValue());
    }

    public static network.foodsharing.event.domain.InvitationStatus toDomain(InvitationStatus status) {
        return status == null ? null : network.foodsharing.event.domain.InvitationStatus.valueOf(status.getValue());
    }

    public static EventViews.LocationDraft toDraft(EventLocation location) {
        return location == null ? null : new EventViews.LocationDraft(location.getName(), location.getStreet(),
                location.getZip(), location.getCity(), location.getLat(), location.getLon());
    }

    public static EventViews.EventDraft toDraft(EventCreate create) {
        return new EventViews.EventDraft(toDomain(create.getType()), create.getName(), create.getDescription(),
                TimeMapper.toInstant(create.getStartsAt()), TimeMapper.toInstant(create.getEndsAt()),
                create.getPublic(), toDraft(create.getLocation()), create.getOnlineLink());
    }

    public static EventViews.EventDraft toDraft(EventUpdate update) {
        return new EventViews.EventDraft(toDomain(update.getType()), update.getName(), update.getDescription(),
                TimeMapper.toInstant(update.getStartsAt()), TimeMapper.toInstant(update.getEndsAt()),
                update.getPublic(), toDraft(update.getLocation()), update.getOnlineLink());
    }

    public List<EventSummary> toSummaries(List<EventViews.EventItem> items) {
        return items.stream().map(EventMapper::toSummary).toList();
    }

    public static EventSummary toSummary(EventViews.EventItem item) {
        network.foodsharing.event.domain.Event event = item.event();
        return new EventSummary().id(event.getId()).regionId(event.getRegionId()).name(event.getName())
                .type(EventType.fromValue(event.getType().name()))
                .startsAt(TimeMapper.toOffset(event.getStartsAt())).endsAt(TimeMapper.toOffset(event.getEndsAt()))
                ._public(event.isPublicEvent()).location(toModel(item.location())).onlineLink(event.getOnlineLink())
                .myStatus(myStatus(item)).acceptedCount(count(item, network.foodsharing.event.domain.InvitationStatus.ACCEPTED))
                .maybeCount(count(item, network.foodsharing.event.domain.InvitationStatus.MAYBE));
    }

    public Event toEvent(EventViews.EventDetails details) {
        EventViews.EventItem item = details.item();
        network.foodsharing.event.domain.Event event = item.event();
        Set<UUID> ids = new HashSet<>();
        ids.add(event.getAuthorId());
        item.invitations().forEach(i -> ids.add(i.getUserId()));
        details.wallPosts().forEach(p -> ids.add(p.getAuthorId()));
        Map<UUID, UserSummary> summaries = summaries(ids);
        EventSummary summary = toSummary(item);
        EventRules.Actor actor = details.actor();
        boolean mayEdit = EventRules.mayEdit(actor, event.getAuthorId());
        Event result = new Event().id(summary.getId()).regionId(summary.getRegionId()).name(summary.getName())
                .type(summary.getType()).startsAt(summary.getStartsAt()).endsAt(summary.getEndsAt())
                ._public(summary.getPublic()).location(summary.getLocation()).onlineLink(summary.getOnlineLink())
                .myStatus(summary.getMyStatus()).acceptedCount(summary.getAcceptedCount())
                .maybeCount(summary.getMaybeCount())
                .description(event.getDescription() == null ? "" : event.getDescription())
                .author(summaries.get(event.getAuthorId())).region(userMapper.regionRef(event.getRegionId()))
                .participants(new EventParticipants()
                        .accepted(users(item.invitations(), network.foodsharing.event.domain.InvitationStatus.ACCEPTED, summaries))
                        .maybe(users(item.invitations(), network.foodsharing.event.domain.InvitationStatus.MAYBE, summaries))
                        .invitedCount(count(item, network.foodsharing.event.domain.InvitationStatus.INVITED)))
                .permissions(new EventPermissions().mayEdit(mayEdit)
                        .mayRsvp(EventRules.mayRsvp(actor, event.isPublicEvent(), event.getEndsAt(), details.now()))
                        .mayComment(EventRules.mayComment(actor, event.isPublicEvent())));
        result.setWallPosts(details.wallPosts().stream().map(p -> toWallPost(p, actor, event.getAuthorId(),
                summaries)).toList());
        return result;
    }

    public EventWallPost toWallPost(network.foodsharing.event.domain.EventWallPost post, EventRules.Actor actor,
                                    UUID eventAuthorId) {
        return toWallPost(post, actor, eventAuthorId, summaries(Set.of(post.getAuthorId())));
    }

    private static EventWallPost toWallPost(network.foodsharing.event.domain.EventWallPost post,
                                            EventRules.Actor actor, UUID eventAuthorId,
                                            Map<UUID, UserSummary> summaries) {
        return new EventWallPost().id(post.getId()).author(summaries.get(post.getAuthorId())).body(post.getBody())
                .createdAt(TimeMapper.toOffset(post.getCreatedAt()))
                .mayDelete(EventRules.mayDeleteWallPost(actor, eventAuthorId, post.getAuthorId()));
    }

    private static EventLocation toModel(network.foodsharing.event.domain.EventLocation location) {
        if (location == null) {
            return null;
        }
        return new EventLocation().name(location.getName()).street(location.getStreet()).zip(location.getZip())
                .city(location.getCity()).lat(location.getLat()).lon(location.getLon());
    }

    private static InvitationStatus myStatus(EventViews.EventItem item) {
        return item.invitations().stream().filter(i -> i.getUserId().equals(item.viewerId())).findFirst()
                .map(i -> InvitationStatus.fromValue(i.getStatus().name())).orElse(null);
    }

    private static int count(EventViews.EventItem item, network.foodsharing.event.domain.InvitationStatus status) {
        return (int) item.invitations().stream().filter(i -> i.getStatus() == status).count();
    }

    private static List<UserSummary> users(List<EventInvitation> invitations,
                                           network.foodsharing.event.domain.InvitationStatus status,
                                           Map<UUID, UserSummary> summaries) {
        return invitations.stream().filter(i -> i.getStatus() == status).map(i -> summaries.get(i.getUserId()))
                .filter(java.util.Objects::nonNull).toList();
    }

    private Map<UUID, UserSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAll(ids).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a, LinkedHashMap::new));
    }
}
