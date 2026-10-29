package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D17.1: the read paths of the load test (e2e/load/k6/main.js) answer 200 on PostgreSQL with the seed data, and
 * concurrent logins of one user all succeed. Both broke only on PostgreSQL / under concurrency and were found by
 * the load test: {@code Instant.MAX} as a query parameter ("timestamp out of range" in the pickup list) and the
 * optimistic lock on {@code user_account.version} when two sessions of one user were created at the same time (409).
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Testcontainers
class LoadPathsPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).as("login-as %s", user).isEqualTo(200);
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private UUID storeOf(SeedUsers user) {
        return jdbc.queryForObject("select store_id from store_membership where user_id = ? and status = 'MEMBER' "
                + "order by store_id limit 1", UUID.class, user.id());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "/api/users/me", "/api/onboarding/journey", "/api/activity?size=20", "/api/bells?size=20",
        "/api/bells/unread-count", "/api/users/me/stores", "/api/users/me/pickups", "/api/conversations?size=20",
        "/api/map/markers/stores", "/api/search?q=brot", "/api/stores/{store}", "/api/stores/{store}/pickups",
    })
    void loadTestReadPathAnswers200(String path) throws Exception {
        Cookie session = login(SeedUsers.FOODSAVER);
        String url = path.replace("{store}", storeOf(SeedUsers.FOODSAVER).toString());
        mvc.perform(get(url).cookie(session)).andExpect(status().isOk());
    }

    @Test
    void concurrentLoginsOfOneUserAllSucceed() throws Exception {
        int parallel = 8;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        try {
            List<Callable<Integer>> logins = new ArrayList<>();
            for (int i = 0; i < parallel; i++) {
                logins.add(() -> mvc.perform(post("/api/dev/login-as").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + SeedUsers.BIEB.id() + "\"}")).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(logins)) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
    }
}
