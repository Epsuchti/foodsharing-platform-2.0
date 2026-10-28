package network.foodsharing.common.seed;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Helper for {@link SeedDateShiftContributor}s: moves timestamp (and DATE) columns of seed rows forward. Only rows
 * whose id starts with the module's seed prefix (docs/seed-ids.md) are touched; DATE columns (marked {@code :date})
 * move by whole days.
 */
public final class SeedDateShifts {

    private SeedDateShifts() {
    }

    /**
     * @param columnsByTable table → columns, e.g. {@code Map.of("basket", List.of("created_at", "expires_at"))};
     *                       a column name ending in {@code :date} is a DATE column
     */
    public static void shift(JdbcTemplate jdbc, String seedPrefix, Duration delta, Map<String, List<String>> columnsByTable) {
        if (delta.isZero()) {
            return;
        }
        String like = seedPrefix + "%";
        columnsByTable.forEach((table, columns) -> {
            for (String spec : columns) {
                boolean date = spec.endsWith(":date");
                String column = date ? spec.substring(0, spec.length() - 5) : spec;
                List<Object[]> rows = jdbc.query("select id, " + column + " from " + table + " where " + column
                        + " is not null and cast(id as varchar(36)) like ?",
                        (rs, n) -> new Object[] {rs.getObject(1, UUID.class), date ? rs.getDate(2) : rs.getTimestamp(2)},
                        like);
                for (Object[] row : rows) {
                    Object shifted = date
                            ? Date.valueOf(((Date) row[1]).toLocalDate().plusDays(delta.toDays()))
                            : Timestamp.from(((Timestamp) row[1]).toInstant().plus(delta));
                    jdbc.update("update " + table + " set " + column + " = ? where id = ?", shifted, row[0]);
                }
            }
        });
    }
}
