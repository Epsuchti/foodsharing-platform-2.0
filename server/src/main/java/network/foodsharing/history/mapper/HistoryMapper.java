package network.foodsharing.history.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.HistoryActionInfo;
import network.foodsharing.api.model.HistoryActor;
import network.foodsharing.api.model.HistoryActorType;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.HistorySubjectType;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.ActorDirectory;
import network.foodsharing.history.ActorDirectory.ActorView;
import network.foodsharing.history.ActorType;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.service.HistoryActionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** History entities → generated models. Used by every controller that shows a history (e.g. the security log). */
@Component
public class HistoryMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectProvider<ActorDirectory> actorDirectory;
    private final ObjectMapper objectMapper;

    public HistoryMapper(ObjectProvider<ActorDirectory> actorDirectory, ObjectMapper objectMapper) {
        this.actorDirectory = actorDirectory;
        this.objectMapper = objectMapper;
    }

    public HistoryEntryPage toPage(Page<HistoryEntry> page) {
        Map<UUID, ActorView> actors = actors(page.getContent());
        HistoryEntryPage result = new HistoryEntryPage();
        result.setItems(page.getContent().stream().map(entry -> toModel(entry, actors)).toList());
        result.setPage(PageMapper.toPageMeta(page));
        return result;
    }

    public Map<UUID, ActorView> actors(Collection<HistoryEntry> entries) {
        List<UUID> ids = entries.stream().map(HistoryEntry::getActorId).filter(Objects::nonNull).distinct().toList();
        ActorDirectory directory = actorDirectory.getIfAvailable();
        if (ids.isEmpty() || directory == null) {
            return Map.of();
        }
        return directory.find(ids);
    }

    public network.foodsharing.api.model.HistoryEntry toModel(HistoryEntry entry, Map<UUID, ActorView> actors) {
        var model = new network.foodsharing.api.model.HistoryEntry();
        model.setId(entry.getId());
        model.setOccurredAt(TimeMapper.toOffset(entry.getOccurredAt()));
        model.setActorType(HistoryActorType.valueOf(entry.getActorType().name()));
        model.setActor(actor(entry, actors));
        model.setSubjectType(HistorySubjectType.valueOf(entry.getSubjectType().name()));
        model.setSubjectId(entry.getSubjectId());
        model.setSecondarySubjectId(entry.getSecondarySubjectId());
        model.setAction(entry.getAction());
        model.setField(entry.getField());
        model.setOldValue(entry.getOldValue());
        model.setNewValue(entry.getNewValue());
        model.setReason(entry.getReason());
        model.setMetadata(metadata(entry.getMetadata()));
        model.setRegionId(entry.getRegionId());
        model.setTraceId(entry.getTraceId());
        return model;
    }

    public List<HistoryActionInfo> toActionInfos(List<HistoryAction> actions) {
        return actions.stream().map(action -> {
            HistoryActionInfo info = new HistoryActionInfo();
            info.setSubjectType(HistorySubjectType.valueOf(action.subjectType().name()));
            info.setAction(action.code());
            info.setLabelKey(HistoryActionRegistry.labelKey(action.subjectType(), action.code()));
            return info;
        }).collect(Collectors.toList());
    }

    public static SubjectType toSubjectType(HistorySubjectType type) {
        return type == null ? null : SubjectType.valueOf(type.name());
    }

    private HistoryActor actor(HistoryEntry entry, Map<UUID, ActorView> actors) {
        HistoryActor actor = new HistoryActor();
        actor.setType(HistoryActorType.valueOf(entry.getActorType().name()));
        if (entry.getActorType() == ActorType.USER && entry.getActorId() != null) {
            ActorView view = actors.get(entry.getActorId());
            actor.setId(entry.getActorId());
            actor.setDisplayName(view == null ? "?" : view.displayName());
            actor.setAvatarUrl(view == null ? null : view.avatarUrl());
            return actor;
        }
        if (entry.getActorType() == ActorType.AUTOMATION) {
            Object name = metadata(entry.getMetadata()) == null ? null : metadata(entry.getMetadata()).get("automation");
            actor.setDisplayName(name == null ? "automation" : String.valueOf(name));
            return actor;
        }
        actor.setDisplayName("system");
        return actor;
    }

    private Map<String, Object> metadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, MAP);
    }
}
