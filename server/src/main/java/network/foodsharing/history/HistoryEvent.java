package network.foodsharing.history;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One thing that happened, ready to be recorded. Create it with {@code HistoryService.event(action, subjectId)}
 * (actor = current user, or SYSTEM when there is none):
 * <pre>history.record(history.event(AuthAction.LOGIN, user.getId()).metadata("method", "GOOGLE").build());</pre>
 */
public final class HistoryEvent {

    private final HistoryAction action;
    private final UUID subjectId;
    private final UUID secondarySubjectId;
    private final String field;
    private final String oldValue;
    private final String newValue;
    private final String reason;
    private final Map<String, Object> metadata;
    private final UUID regionId;
    private final ActorType actorType;
    private final UUID actorId;
    private final String automationName;

    private HistoryEvent(Builder builder) {
        this.action = builder.action;
        this.subjectId = builder.subjectId;
        this.secondarySubjectId = builder.secondarySubjectId;
        this.field = builder.field;
        this.oldValue = builder.oldValue;
        this.newValue = builder.newValue;
        this.reason = builder.reason;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.regionId = builder.regionId;
        this.actorType = builder.actorType;
        this.actorId = builder.actorId;
        this.automationName = builder.automationName;
    }

    public static Builder builder(HistoryAction action, UUID subjectId) {
        return new Builder(action, subjectId);
    }

    public HistoryAction action() {
        return action;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public UUID secondarySubjectId() {
        return secondarySubjectId;
    }

    public String field() {
        return field;
    }

    public String oldValue() {
        return oldValue;
    }

    public String newValue() {
        return newValue;
    }

    public String reason() {
        return reason;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public UUID regionId() {
        return regionId;
    }

    public ActorType actorType() {
        return actorType;
    }

    public UUID actorId() {
        return actorId;
    }

    public String automationName() {
        return automationName;
    }

    public static final class Builder {

        private final HistoryAction action;
        private final UUID subjectId;
        private UUID secondarySubjectId;
        private String field;
        private String oldValue;
        private String newValue;
        private String reason;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private UUID regionId;
        private ActorType actorType = ActorType.SYSTEM;
        private UUID actorId;
        private String automationName;

        private Builder(HistoryAction action, UUID subjectId) {
            this.action = Objects.requireNonNull(action, "action");
            this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        }

        public Builder secondarySubject(UUID id) {
            this.secondarySubjectId = id;
            return this;
        }

        public Builder field(String field) {
            this.field = field;
            return this;
        }

        public Builder oldValue(String value) {
            this.oldValue = value;
            return this;
        }

        public Builder newValue(String value) {
            this.newValue = value;
            return this;
        }

        /**
         * Shortcut for field + old + new. Enums are stored by constant name (as the {@code @Tracked} listener and
         * the database columns do), not by {@code toString()} (generated API enums return their JSON value).
         */
        public Builder change(String field, Object oldValue, Object newValue) {
            this.field = field;
            this.oldValue = asText(oldValue);
            this.newValue = asText(newValue);
            return this;
        }

        private static String asText(Object value) {
            if (value == null) {
                return null;
            }
            return value instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(value);
        }

        /** Overrides the request's {@link HistoryContext#reason()}. */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /** Serialised to JSON (max 32600 characters). */
        public Builder metadata(Map<String, ?> values) {
            if (values != null) {
                this.metadata.putAll(values);
            }
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder region(UUID regionId) {
            this.regionId = regionId;
            return this;
        }

        public Builder actorUser(UUID userId) {
            this.actorType = ActorType.USER;
            this.actorId = Objects.requireNonNull(userId);
            this.automationName = null;
            return this;
        }

        public Builder actorSystem() {
            this.actorType = ActorType.SYSTEM;
            this.actorId = null;
            this.automationName = null;
            return this;
        }

        public Builder actorAutomation(String name) {
            this.actorType = ActorType.AUTOMATION;
            this.actorId = null;
            this.automationName = Objects.requireNonNull(name);
            return this;
        }

        public HistoryEvent build() {
            return new HistoryEvent(this);
        }
    }
}
