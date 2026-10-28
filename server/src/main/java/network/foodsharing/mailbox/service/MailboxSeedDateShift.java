package network.foodsharing.mailbox.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded mails (seed module 0042) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class MailboxSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000042-";

    private final JdbcTemplate jdbc;

    public MailboxSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        shiftColumn("mail_message", "received_at", delta);
        shiftColumn("mail_message", "read_at", delta);
        shiftColumn("mail_message", "answered_at", delta);
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
