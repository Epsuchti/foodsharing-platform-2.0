package network.foodsharing.history.internal;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.HistorySubject;
import network.foodsharing.history.Tracked;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.service.HistoryService;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Hibernate post-update listener: for every dirty {@link Tracked} property of a {@link HistorySubject} it writes a
 * {@code FIELD_CHANGED} entry (field, old, new; {@code ***} when redacted). The row is inserted with plain JDBC on
 * the session's connection, because persisting entities while Hibernate is flushing is not allowed. Fields the
 * transaction already recorded explicitly ({@code HistoryService.record} with {@code field}) are skipped.
 */
public class TrackedFieldListener implements PostUpdateEventListener {

    public static final String REDACTED = "***";

    private final ObjectProvider<HistoryService> historyService;
    private final Map<Class<?>, Map<String, Tracked>> trackedFieldsByType = new ConcurrentHashMap<>();

    public TrackedFieldListener(ObjectProvider<HistoryService> historyService) {
        this.historyService = historyService;
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!(event.getEntity() instanceof HistorySubject subject)) {
            return;
        }
        Map<String, Tracked> tracked = trackedFields(event.getEntity().getClass());
        Object[] oldState = event.getOldState();
        Object[] state = event.getState();
        if (tracked.isEmpty() || oldState == null || state == null) {
            return;
        }
        HistoryService history = historyService.getObject();
        String[] names = event.getPersister().getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            Tracked annotation = tracked.get(names[i]);
            if (annotation == null || Objects.equals(oldState[i], state[i])) {
                continue;
            }
            if (history.wasRecordedExplicitly(subject.historySubjectType(), subject.historySubjectId(), names[i])) {
                continue;
            }
            HistoryEvent.Builder builder = history.event(subject.historyFieldChangedAction(), subject.historySubjectId())
                    .region(subject.historyRegionId());
            if (annotation.redact()) {
                builder.change(names[i], oldState[i] == null ? null : REDACTED, state[i] == null ? null : REDACTED);
            } else {
                builder.change(names[i], asText(oldState[i]), asText(state[i]));
            }
            HistoryEntry entry = history.toEntry(builder.build());
            event.getSession().doWork(connection -> HistoryEntryJdbcWriter.insert(connection, entry));
        }
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private Map<String, Tracked> trackedFields(Class<?> type) {
        return trackedFieldsByType.computeIfAbsent(type, TrackedFieldListener::scan);
    }

    private static Map<String, Tracked> scan(Class<?> type) {
        Map<String, Tracked> result = new ConcurrentHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                Tracked tracked = field.getAnnotation(Tracked.class);
                if (tracked != null) {
                    result.putIfAbsent(field.getName(), tracked);
                }
            }
        }
        return result;
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return String.valueOf(value);
    }
}
