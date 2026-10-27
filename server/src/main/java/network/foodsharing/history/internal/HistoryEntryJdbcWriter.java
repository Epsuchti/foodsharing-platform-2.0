package network.foodsharing.history.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.UUID;
import network.foodsharing.history.domain.HistoryEntry;

/** Inserts a history entry with JDBC (used from inside a Hibernate flush, see {@link TrackedFieldListener}). */
final class HistoryEntryJdbcWriter {

    private static final String SQL = "insert into history_entry (id, version, created_at, updated_at, created_by, "
            + "updated_by, occurred_at, actor_id, actor_type, subject_type, subject_id, secondary_subject_id, action, "
            + "field, old_value, new_value, reason, metadata, region_id, trace_id) "
            + "values (?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private HistoryEntryJdbcWriter() {
    }

    static void insert(Connection connection, HistoryEntry entry) throws SQLException {
        UUID id = UUID.randomUUID();
        var occurredAt = entry.getOccurredAt().atOffset(ZoneOffset.UTC);
        try (PreparedStatement statement = connection.prepareStatement(SQL)) {
            int i = 1;
            statement.setObject(i++, id);
            statement.setObject(i++, occurredAt);
            statement.setObject(i++, occurredAt);
            setUuid(statement, i++, entry.getActorId());
            setUuid(statement, i++, entry.getActorId());
            statement.setObject(i++, occurredAt);
            setUuid(statement, i++, entry.getActorId());
            statement.setString(i++, entry.getActorType().name());
            statement.setString(i++, entry.getSubjectType().name());
            statement.setObject(i++, entry.getSubjectId());
            setUuid(statement, i++, entry.getSecondarySubjectId());
            statement.setString(i++, entry.getAction());
            statement.setString(i++, entry.getField());
            statement.setString(i++, entry.getOldValue());
            statement.setString(i++, entry.getNewValue());
            statement.setString(i++, entry.getReason());
            statement.setString(i++, entry.getMetadata());
            setUuid(statement, i++, entry.getRegionId());
            statement.setString(i, entry.getTraceId());
            statement.executeUpdate();
        }
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }
}
