package network.foodsharing.history.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.common.error.ApiErrors;
import network.foodsharing.history.ActorType;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistoryContext;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.security.CurrentActorService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Records history entries in the same transaction as the change (plan §10.1). Every mutating service method calls
 * {@link #record(HistoryEvent)} (ArchUnit rule 4).
 */
@Service
public class HistoryService {

    /** Subject id for {@link SubjectType#SYSTEM} entries. */
    public static final UUID SYSTEM_SUBJECT_ID = new UUID(0L, 0L);

    private static final Object EXPLICIT_FIELDS_KEY = new Object();

    private final HistoryEntryRepository repository;
    private final CurrentActorService currentActorService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public HistoryService(HistoryEntryRepository repository, CurrentActorService currentActorService, Clock clock,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.currentActorService = currentActorService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /** Distinct subject ids that ever got {@code action} in the region (lookup for the owning module). */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> subjectsWithAction(HistoryAction action, UUID regionId) {
        return new java.util.LinkedHashSet<>(repository.subjectIds(action.subjectType(), action.code(), regionId));
    }

    /** Starts an event; actor = signed-in user, or SYSTEM when there is none. */
    public HistoryEvent.Builder event(HistoryAction action, UUID subjectId) {
        HistoryEvent.Builder builder = HistoryEvent.builder(action, subjectId);
        currentActorService.currentUserId().ifPresent(builder::actorUser);
        return builder;
    }

    /** Records the event in the current transaction (or a new one when none is active). */
    @Transactional
    public HistoryEntry record(HistoryEvent event) {
        HistoryEntry entry = toEntry(event);
        rememberExplicitField(entry);
        return repository.save(entry);
    }

    /**
     * Records the event in its own transaction, e.g. a failed login whose surrounding transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HistoryEntry recordInNewTransaction(HistoryEvent event) {
        return repository.save(toEntry(event));
    }

    /**
     * Builds (does not persist) the entry for an event: occurredAt from the clock, reason from the event or
     * {@link HistoryContext}, trace id from the MDC, metadata as JSON.
     */
    public HistoryEntry toEntry(HistoryEvent event) {
        HistoryAction action = event.action();
        HistoryEntry entry = new HistoryEntry();
        entry.setOccurredAt(clock.instant());
        entry.setActorType(event.actorType());
        entry.setActorId(event.actorType() == ActorType.USER ? event.actorId() : null);
        entry.setSubjectType(action.subjectType());
        entry.setSubjectId(event.subjectId());
        entry.setSecondarySubjectId(event.secondarySubjectId());
        entry.setAction(action.code());
        entry.setField(truncate(event.field(), 100));
        entry.setOldValue(truncate(event.oldValue(), HistoryEntry.MAX_VALUE_LENGTH));
        entry.setNewValue(truncate(event.newValue(), HistoryEntry.MAX_VALUE_LENGTH));
        String reason = event.reason() != null ? event.reason() : HistoryContext.reason().orElse(null);
        entry.setReason(truncate(reason, HistoryEntry.MAX_REASON_LENGTH));
        entry.setMetadata(metadataJson(event));
        entry.setRegionId(event.regionId());
        entry.setTraceId(Optional.ofNullable(MDC.get(ApiErrors.TRACE_ID_MDC_KEY)).map(id -> truncate(id, 32)).orElse(null));
        return entry;
    }

    /**
     * True when the current transaction already recorded this field of this subject explicitly (then the
     * {@code @Tracked} listener does not add a duplicate FIELD_CHANGED entry).
     */
    public boolean wasRecordedExplicitly(SubjectType subjectType, UUID subjectId, String field) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Set<String> fields = (Set<String>) TransactionSynchronizationManager.getResource(EXPLICIT_FIELDS_KEY);
        return fields != null && fields.contains(key(subjectType, subjectId, field));
    }

    private void rememberExplicitField(HistoryEntry entry) {
        if (entry.getField() == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Set<String> fields = (Set<String>) TransactionSynchronizationManager.getResource(EXPLICIT_FIELDS_KEY);
        if (fields == null) {
            Set<String> created = new HashSet<>();
            TransactionSynchronizationManager.bindResource(EXPLICIT_FIELDS_KEY, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(EXPLICIT_FIELDS_KEY);
                }
            });
            fields = created;
        }
        fields.add(key(entry.getSubjectType(), entry.getSubjectId(), entry.getField()));
    }

    private String metadataJson(HistoryEvent event) {
        var metadata = new java.util.LinkedHashMap<String, Object>(event.metadata());
        if (event.automationName() != null) {
            metadata.put("automation", event.automationName());
        }
        if (metadata.isEmpty()) {
            return null;
        }
        String json = objectMapper.writeValueAsString(metadata);
        if (json.length() > HistoryEntry.MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("History metadata exceeds " + HistoryEntry.MAX_VALUE_LENGTH + " characters");
        }
        return json;
    }

    private static String key(SubjectType type, UUID id, String field) {
        return type + ":" + id + ":" + field;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
