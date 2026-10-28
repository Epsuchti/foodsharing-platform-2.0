package network.foodsharing.notification.telegram.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The single {@code telegram_offset} row (id 1): the last processed Bot API {@code update_id} (A28). */
@Repository
public class TelegramOffsetRepository {

    private final JdbcClient jdbc;

    public TelegramOffsetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> lastUpdateId() {
        return jdbc.sql("select last_update_id from telegram_offset where id = 1").query(Long.class).optional();
    }

    public void save(long lastUpdateId, Instant now) {
        int updated = jdbc.sql("update telegram_offset set last_update_id = :id, updated_at = :now where id = 1")
                .param("id", lastUpdateId).param("now", Timestamp.from(now)).update();
        if (updated == 0) {
            jdbc.sql("insert into telegram_offset (id, last_update_id, updated_at) values (1, :id, :now)")
                    .param("id", lastUpdateId).param("now", Timestamp.from(now)).update();
        }
    }
}
