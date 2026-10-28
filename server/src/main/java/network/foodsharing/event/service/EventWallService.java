package network.foodsharing.event.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventAction;
import network.foodsharing.event.domain.EventWallPost;
import network.foodsharing.event.repository.EventWallPostRepository;
import network.foodsharing.event.service.EventPermissionService.EventContext;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Event wall (E2): comments by everyone who may see the event; {@code EVENT_POST} to author and attendees. */
@Service
public class EventWallService {

    private final EventWallPostRepository posts;
    private final EventPermissionService access;
    private final EventService events;
    private final EventNotifier notifier;
    private final UserService users;
    private final HistoryService history;
    private final java.time.Clock clock;

    public EventWallService(EventWallPostRepository posts, EventPermissionService access, EventService events,
                            EventNotifier notifier, UserService users, HistoryService history, java.time.Clock clock) {
        this.posts = posts;
        this.access = access;
        this.events = events;
        this.notifier = notifier;
        this.users = users;
        this.history = history;
        this.clock = clock;
    }

    /** @throws ApiException 404 EVENT_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_FAILED (blank body) */
    @Transactional
    public EventWallPost create(UUID eventId, String body) {
        EventContext context = access.visibleEvent(eventId);
        Event event = context.event();
        EventPermissionService.require(EventRules.mayComment(context.actor(), event.isPublicEvent()));
        if (body == null || body.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        EventWallPost post = new EventWallPost();
        post.setEventId(eventId);
        post.setAuthorId(context.actor().userId());
        post.setBody(body.strip());
        post = posts.save(post);
        history.record(history.event(EventAction.WALL_POST_CREATED, eventId).region(event.getRegionId())
                .metadata("postId", post.getId().toString()).build());
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(event.getAuthorId());
        recipients.addAll(events.attendees(eventId));
        String poster = users.findActive(context.actor().userId()).map(User::getDisplayName).orElse("");
        notifier.wallPost(event, recipients, context.actor().userId(), poster);
        return post;
    }

    /** @throws ApiException 404 EVENT_NOT_FOUND / WALL_POST_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID eventId, UUID postId) {
        EventContext context = access.visibleEvent(eventId);
        EventWallPost post = posts.findById(postId)
                .filter(p -> p.getEventId().equals(eventId) && p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.WALL_POST_NOT_FOUND));
        EventPermissionService.require(EventRules.mayDeleteWallPost(context.actor(), context.event().getAuthorId(),
                post.getAuthorId()));
        post.setDeletedAt(clock.instant());
        posts.save(post);
        history.record(history.event(EventAction.WALL_POST_DELETED, eventId).region(context.event().getRegionId())
                .metadata("postId", postId.toString()).build());
    }

    /** A wall post for the activity feed (A19). */
    public record FeedPost(UUID id, UUID eventId, String eventName, UUID authorId, String body,
                           java.time.Instant createdAt) {
    }

    /** Activity feed (A19): newest wall posts of the events the user accepted. */
    @Transactional(readOnly = true)
    public java.util.List<FeedPost> latestOfAcceptedEvents(UUID userId, java.time.Instant before, int limit) {
        java.util.Map<UUID, String> names = new java.util.HashMap<>();
        events.listForCalendar(userId, java.util.Set.of(network.foodsharing.event.domain.InvitationStatus.ACCEPTED),
                java.time.Instant.EPOCH).forEach(i -> names.put(i.event().getId(), i.event().getName()));
        if (names.isEmpty()) {
            return java.util.List.of();
        }
        return posts.findByEventIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDesc(names.keySet(), before,
                        org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .map(p -> new FeedPost(p.getId(), p.getEventId(), names.get(p.getEventId()), p.getAuthorId(),
                        p.getBody(), p.getCreatedAt())).toList();
    }
}
