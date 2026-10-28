package network.foodsharing.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import network.foodsharing.messaging.service.MessagingService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A33: concurrent senders get gap-free, unique seqs (conversation row lock) on PostgreSQL. */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema,seed", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class MessageSeqPostgresIT {

    static final UUID DIRECT = UUID.fromString("00000041-0001-4000-8000-000000000001");
    static final int PER_SENDER = 15;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MessagingService messaging;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentSendersGetUniqueContiguousSeqs() throws Exception {
        long before = jdbc.queryForObject("select last_seq from conversation where id = ?", Long.class, DIRECT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (UUID sender : List.of(SeedUsers.FOODSAVER.id(), SeedUsers.BIEB.id())) {
            Callable<Integer> task = () -> {
                start.await();
                for (int i = 0; i < PER_SENDER; i++) {
                    messaging.send(sender, DIRECT, "msg " + i, null);
                }
                return PER_SENDER;
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(PER_SENDER);
        }
        pool.shutdown();
        List<Long> seqs = jdbc.queryForList("select seq from message where conversation_id = ? and seq > ? order by seq",
                Long.class, DIRECT, before);
        assertThat(seqs).hasSize(2 * PER_SENDER);
        for (int i = 0; i < seqs.size(); i++) {
            assertThat(seqs.get(i)).isEqualTo(before + 1 + i);
        }
        assertThat(jdbc.queryForObject("select last_seq from conversation where id = ?", Long.class, DIRECT))
                .isEqualTo(before + 2 * PER_SENDER);
    }
}
