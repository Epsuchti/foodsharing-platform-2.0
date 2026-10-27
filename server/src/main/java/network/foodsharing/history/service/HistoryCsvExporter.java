package network.foodsharing.history.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.history.ActorDirectory.ActorView;
import network.foodsharing.history.domain.HistoryEntry;
import org.springframework.stereotype.Component;

/** RFC 4180 CSV of history entries; cells starting with = + - @ are prefixed with ' (CSV injection). */
@Component
public class HistoryCsvExporter {

    static final String HEADER = "occurredAt,actorType,actorId,actorName,subjectType,subjectId,secondarySubjectId,"
            + "action,field,oldValue,newValue,reason,regionId,metadata,traceId";

    public String toCsv(List<HistoryEntry> entries, Map<UUID, ActorView> actors) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (HistoryEntry entry : entries) {
            ActorView actor = entry.getActorId() == null ? null : actors.get(entry.getActorId());
            appendRow(csv,
                    entry.getOccurredAt(),
                    entry.getActorType(),
                    entry.getActorId(),
                    actor == null ? null : actor.displayName(),
                    entry.getSubjectType(),
                    entry.getSubjectId(),
                    entry.getSecondarySubjectId(),
                    entry.getAction(),
                    entry.getField(),
                    entry.getOldValue(),
                    entry.getNewValue(),
                    entry.getReason(),
                    entry.getRegionId(),
                    entry.getMetadata(),
                    entry.getTraceId());
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(cells[i] == null ? "" : String.valueOf(cells[i])));
        }
        csv.append("\r\n");
    }

    static String escape(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
