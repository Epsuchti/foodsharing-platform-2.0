package network.foodsharing.notification.service;

import java.time.Clock;
import network.foodsharing.common.realtime.RealtimeEvent;
import network.foodsharing.common.realtime.RealtimeEventType;
import network.foodsharing.common.realtime.RealtimePublisher;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.repository.BellRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Persisted bells (UI in M4). Identifiers follow upstream {@code BellType::createIdentifier}. */
@Service
public class BellService {

    private final BellRepository repository;
    private final HistoryService history;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<RealtimePublisher> realtime;

    public BellService(BellRepository repository, HistoryService history, ObjectMapper objectMapper, Clock clock,
                       org.springframework.beans.factory.ObjectProvider<RealtimePublisher> realtime) {
        this.repository = repository;
        this.history = history;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.realtime = realtime;
    }

    /** A32: tells the user's open tabs about a new or removed bell (they refetch the list). */
    private void publish(UUID userId, UUID bellId) {
        RealtimePublisher publisher = realtime.getIfAvailable();
        if (publisher == null) {
            return;
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        if (bellId != null) {
            payload.put("bellId", bellId.toString());
        }
        payload.put("unreadCount", repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId));
        publisher.toUsers(java.util.Set.of(userId), new RealtimeEvent(RealtimeEventType.BELL, clock.instant(), payload));
    }

    /** {@code <TYPE>-<id>-<id>…}, e.g. {@code REGION_NEW_MEMBER-<userId>}. */
    public static String identifier(NotificationType type, Object... ids) {
        StringBuilder builder = new StringBuilder(type.name());
        for (Object id : ids) {
            builder.append('-').append(id);
        }
        return builder.toString();
    }

    /** Creates one bell and records BELL_CREATED (subject SYSTEM, metadata type + user). */
    @Transactional
    public Bell create(UUID userId, NotificationType type, Map<String, String> params, String link, String identifier,
                       String icon, UUID mailOutboxId) {
        Bell bell = new Bell();
        bell.setUserId(userId);
        bell.setType(type);
        bell.setTitleKey(type == NotificationType.GENERIC && params.containsKey("titleKey")
                ? params.get("titleKey") : type.defaultTitleKey());
        bell.setParams(params.isEmpty() ? null : objectMapper.writeValueAsString(params));
        bell.setLink(link);
        bell.setIdentifier(identifier);
        bell.setIcon(icon);
        bell.setMailOutboxId(mailOutboxId);
        Bell saved = repository.save(bell);
        history.record(history.event(NotificationAction.BELL_CREATED, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(userId)
                .metadata(Map.of("type", type.name(), "bellId", saved.getId().toString()))
                .build());
        publish(userId, saved.getId());
        return saved;
    }

    /** Soft-deletes every bell with this identifier (e.g. when the triggering situation is resolved). */
    @Transactional
    @NoHistory(reason = "bells are transient hints; the action that resolves them records its own history")
    public int deleteByIdentifier(String identifier) {
        List<Bell> bells = repository.findByIdentifierAndDeletedAtIsNull(identifier);
        bells.forEach(bell -> bell.setDeletedAt(clock.instant()));
        repository.flush();
        bells.stream().map(Bell::getUserId).distinct().forEach(userId -> publish(userId, null));
        return bells.size();
    }

    /** My visible bells, unread first, newest first (N3). */
    @Transactional(readOnly = true)
    public Page<Bell> page(UUID userId, boolean unreadOnly, int page, int size) {
        return repository.page(userId, unreadOnly, PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNullAndDeletedAtIsNull(userId);
    }

    /** Marks own bells read; unknown or foreign ids are ignored. */
    @Transactional
    public int markRead(UUID userId, Collection<UUID> ids) {
        int changed = ids.isEmpty() ? 0 : repository.markRead(userId, ids, clock.instant());
        recordBulk(NotificationAction.BELL_READ, userId, changed);
        return changed;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        int changed = repository.markAllRead(userId, clock.instant());
        recordBulk(NotificationAction.BELL_READ, userId, changed);
        return changed;
    }

    /** Soft-deletes one own bell. @throws ApiException 404 BELL_NOT_FOUND */
    @Transactional
    public void delete(UUID userId, UUID bellId) {
        Bell bell = repository.findByIdAndUserIdAndDeletedAtIsNull(bellId, userId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BELL_NOT_FOUND));
        bell.setDeletedAt(clock.instant());
        recordBulk(NotificationAction.BELL_DELETED, userId, 1);
    }

    /** Soft-deletes all own bells, or only the read ones. */
    @Transactional
    public int deleteAll(UUID userId, boolean readOnly) {
        int changed = repository.deleteAll(userId, readOnly, clock.instant());
        recordBulk(NotificationAction.BELL_DELETED, userId, changed);
        return changed;
    }

    /** Marks a bell as pushed (web push channel). */
    @Transactional
    @NoHistory(reason = "delivery marker; the notification itself is recorded as NOTIFICATION_SENT")
    public void markPushed(UUID bellId) {
        repository.findById(bellId).ifPresent(bell -> bell.setPushedAt(clock.instant()));
    }

    private void recordBulk(NotificationAction action, UUID userId, int count) {
        if (count > 0) {
            history.record(history.event(action, HistoryService.SYSTEM_SUBJECT_ID).secondarySubject(userId)
                    .metadata(Map.of("count", count)).build());
        }
    }

    @Transactional(readOnly = true)
    public boolean existsSince(String identifier, java.time.Instant since) {
        return repository.existsByIdentifierAndCreatedAtAfter(identifier, since);
    }

    @Transactional(readOnly = true)
    public List<Bell> search(UUID userId, NotificationType type) {
        return repository.search(userId, type);
    }

    /** Parameters of a bell as a map (empty when none). */
    @SuppressWarnings("unchecked")
    public Map<String, String> params(Bell bell) {
        if (bell.getParams() == null || bell.getParams().isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(bell.getParams(), Map.class);
    }
}
