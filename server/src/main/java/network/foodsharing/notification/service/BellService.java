package network.foodsharing.notification.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.repository.BellRepository;
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

    public BellService(BellRepository repository, HistoryService history, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.history = history;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
        return saved;
    }

    /** Soft-deletes every bell with this identifier (e.g. when the triggering situation is resolved). */
    @Transactional
    @NoHistory(reason = "bells are transient hints; the action that resolves them records its own history")
    public int deleteByIdentifier(String identifier) {
        List<Bell> bells = repository.findByIdentifierAndDeletedAtIsNull(identifier);
        bells.forEach(bell -> bell.setDeletedAt(clock.instant()));
        return bells.size();
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
