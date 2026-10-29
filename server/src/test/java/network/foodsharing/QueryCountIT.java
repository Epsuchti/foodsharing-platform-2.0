package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * N+1 guard (hardening): the main list endpoints on the seed data issue a bounded number of Hibernate statements
 * (Hibernate statistics; JdbcTemplate read models are not counted). The bounds sit a little above today's counts, so a
 * per-row lazy load or per-row permission query shows up as a failure.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:querycount-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class QueryCountIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String STORE_S01 = "00000020-0001-4000-8000-000000000001";
    private static final String THREAD = "00000043-0001-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
        "BOT|/api/bells|4",
        "BOT|/api/conversations|11",
        "BOT|/api/mailboxes|15",
        "BOT|/api/regions/" + ZUERICH + "/members|20",
        "BOT|/api/regions/" + ZUERICH + "/stores|14",
        "BOT|/api/regions/" + ZUERICH + "/forum/threads|17",
        "BOT|/api/forum/threads/" + THREAD + "|19",
        "BOT|/api/regions/" + ZUERICH + "/events|9",
        "BOT|/api/regions/" + ZUERICH + "/polls|12",
        "BOT|/api/events/mine|6",
        "BIEB|/api/stores/" + STORE_S01 + "/team|19",
        "BIEB|/api/users/me/pickups|20",
        // D17.1 load-test paths (per-kind loops of the journey/session are constant, not per row)
        "FOODSAVER|/api/auth/session|50",
        "FOODSAVER|/api/onboarding/journey|40",
        "FOODSAVER|/api/activity|30",
        "FOODSAVER|/api/users/me/stores|13",
        "FOODSAVER|/api/map/markers/stores|3",
        "FOODSAVER|/api/stores/" + STORE_S01 + "/pickups|26",
        "FOODSAVER|/api/search?q=brot|4",
    })
    void listEndpointStaysWithinItsStatementBudget(SeedUsers user, String path, long budget) throws Exception {
        Cookie session = login(user);
        mvc.perform(get(path).cookie(session)).andExpect(status().isOk()); // warm-up (caches, lazy init)
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get(path).cookie(session)).andExpect(status().isOk());
        long statements = statistics.getPrepareStatementCount();
        assertThat(statements).as("Hibernate statements for GET %s: %s", path, breakdown(statistics))
                .isLessThanOrEqualTo(budget);
    }

    /** Entity fetches and query executions, to find the culprit when a budget is exceeded. */
    private static String breakdown(Statistics statistics) {
        StringBuilder text = new StringBuilder();
        for (String entity : statistics.getEntityNames()) {
            var entityStats = statistics.getEntityStatistics(entity);
            if (entityStats.getFetchCount() + entityStats.getLoadCount() > 0) {
                text.append("\n  entity ").append(entity.substring(entity.lastIndexOf('.') + 1)).append(" fetch=")
                        .append(entityStats.getFetchCount()).append(" load=").append(entityStats.getLoadCount());
            }
        }
        for (String query : statistics.getQueries()) {
            long count = statistics.getQueryStatistics(query).getExecutionCount();
            if (count > 0) {
                text.append("\n  ").append(count).append("x ").append(query, 0, Math.min(query.length(), 160));
            }
        }
        return text.toString();
    }

    /** Paged lists: one row or twenty rows must cost the same number of statements (no per-row query). */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
        "BOT|/api/regions/" + ZUERICH + "/members",
        "BOT|/api/regions/" + ZUERICH + "/forum/threads",
        "BOT|/api/bells",
    })
    void pagedListCostDoesNotGrowWithThePageSize(SeedUsers user, String path) throws Exception {
        Cookie session = login(user);
        long one = count(session, path + "?size=1");
        long many = count(session, path + "?size=20");
        // constant cost (+1 tolerated for a batch query that a one-row page may skip)
        assertThat(many).as("statements for %s?size=20 vs size=1", path).isLessThanOrEqualTo(one + 1);
    }

    private long count(Cookie session, String path) throws Exception {
        mvc.perform(get(path).cookie(session)).andExpect(status().isOk());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get(path).cookie(session)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }
}
