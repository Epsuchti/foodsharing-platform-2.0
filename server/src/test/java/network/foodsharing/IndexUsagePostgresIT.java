package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * B6/B9/B14 hardening: the hot list/job queries can use their indexes on PostgreSQL (incl. the pg_trgm GIN index of
 * the global search). Sequential scans are switched off for the session, so the plan shows the index whenever the
 * index is usable for the query shape (the seed tables are too small for the planner to prefer it on its own).
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema,seed", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class IndexUsagePostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "ix_search_index_text_trgm|select id from search_index where search_text like '%zurich%'",
        "ix_basket_status_expires_at|select id from basket where status = 'ACTIVE' and expires_at < now()",
        "ix_report_region_status|select id from report where region_id = '00000000-0000-0000-0000-000000000001'"
                + " and status = 'NEW'",
        "uq_message_conversation_seq|select id from message where conversation_id = "
                + "'00000000-0000-0000-0000-000000000001' and seq > 10 order by seq",
        "ix_mail_message_mailbox_folder_received|select id from mail_message where mailbox_id = "
                + "'00000000-0000-0000-0000-000000000001' and folder = 'INBOX' order by received_at desc limit 20",
        "ix_forum_thread_listing|select id from forum_thread where region_id = '00000000-0000-0000-0000-000000000001'"
                + " and ambassador_forum = false and active = true order by sticky desc, last_post_at desc limit 20",
        "ix_event_region_starts|select id from event where region_id = '00000000-0000-0000-0000-000000000001'"
                + " and starts_at > now()",
        "ix_poll_region_starts|select id from poll where region_id = '00000000-0000-0000-0000-000000000001'"
                + " and starts_at > now()",
    })
    void queryUsesIndex(String index, String query) {
        String plan = String.join("\n", explain(query));
        assertThat(plan).as(query).contains(index);
    }

    private List<String> explain(String query) {
        return jdbc.execute((ConnectionCallback<List<String>>) connection -> {
            List<String> lines = new ArrayList<>();
            try (Statement statement = connection.createStatement()) {
                statement.execute("set enable_seqscan = off");
                try (ResultSet rs = statement.executeQuery("explain " + query)) {
                    while (rs.next()) {
                        lines.add(rs.getString(1));
                    }
                } finally {
                    statement.execute("reset enable_seqscan");
                }
            }
            return lines;
        });
    }
}
