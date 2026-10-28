package network.foodsharing.messaging.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded conversations and messages (seed module 0041) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class MessagingSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000041-";

    private final JdbcTemplate jdbc;

    public MessagingSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        shiftColumn("conversation", "last_message_at", delta);
        shiftColumn("conversation_member", "joined_at", delta);
        shiftColumn("message", "sent_at", delta);
    }

    private void shiftColumn(String table, String column, Duration delta) {
        List<Object[]> rows = jdbc.query("select id, " + column + " from " + table + " where " + column
                + " is not null", (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getTimestamp(2)});
        for (Object[] row : rows) {
            if (!row[0].toString().startsWith(SEED_PREFIX)) {
                continue;
            }
            Timestamp shifted = Timestamp.from(((Timestamp) row[1]).toInstant().plus(delta));
            jdbc.update("update " + table + " set " + column + " = ? where id = ?", shifted, row[0]);
        }
    }
}
