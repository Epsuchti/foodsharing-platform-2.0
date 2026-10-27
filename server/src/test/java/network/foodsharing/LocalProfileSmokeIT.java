package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.config.DevSecurityConfig;
import network.foodsharing.dev.service.DevService;
import network.foodsharing.dev.service.DevUserCatalog;
import network.foodsharing.dev.service.FakeOidcProvider;
import network.foodsharing.dev.service.SeedResetService;
import network.foodsharing.dev.web.DevController;
import network.foodsharing.dev.web.FakeOidcController;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Boots the {@code local} profile (on an in-memory H2 instead of the file database) with the seed context and checks
 * that the seed users and the dev beans are there and the dev login works end to end.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:local-smoke-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalProfileSmokeIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void devBeansArePresent() {
        for (Class<?> type : List.of(DevController.class, FakeOidcController.class, DevService.class,
                SeedResetService.class, DevUserCatalog.class, FakeOidcProvider.class, DevSecurityConfig.class)) {
            assertThat(context.getBeanNamesForType(type)).as(type.getSimpleName()).hasSize(1);
        }
    }

    @Test
    void seedUsersAreLoadedWithTheirFixedIdsAndRoles() {
        Map<String, Map<String, Object>> users = jdbc.queryForList(
                        "select id, email, role, preferred_language, verified, foodsaver_number, avatar_url from user_account")
                .stream().collect(Collectors.toMap(row -> String.valueOf(row.get("ID")), row -> row));
        assertThat(users).hasSize(SeedUsers.values().length);
        for (SeedUsers seed : SeedUsers.values()) {
            Map<String, Object> row = users.get(seed.id().toString());
            assertThat(row).as(seed.name()).isNotNull();
            assertThat(row.get("EMAIL")).isEqualTo(seed.email());
            assertThat(row.get("ROLE")).isEqualTo(seed.role().name());
            assertThat(row.get("AVATAR_URL")).isEqualTo("/images/avatars/" + seed.name().toLowerCase() + ".svg");
        }
        Set<Object> languages = users.values().stream().map(row -> row.get("PREFERRED_LANGUAGE")).collect(Collectors.toSet());
        assertThat(languages).containsExactlyInAnyOrder("DE", "EN", "GWS", "FR", "IT");
        assertThat(jdbc.queryForObject("select count(*) from history_entry", Integer.class)).isGreaterThanOrEqualTo(40);
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'ACCOUNT_CREATED'",
                Integer.class)).isEqualTo(SeedUsers.values().length);
    }

    @Test
    void devUserSwitcherListsAllSeedUsersAndLogsIn() throws Exception {
        mvc.perform(get("/api/dev/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SeedUsers.values().length))
                .andExpect(jsonPath("$[0].email").value("newbie@local"))
                .andExpect(jsonPath("$[0].descriptionKey").value("dev.users.newbie"))
                .andExpect(jsonPath("$[13].email").value("orga@local"))
                .andExpect(jsonPath("$[13].role").value("ORGA"));

        MvcResult login = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + SeedUsers.ORGA.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("orga@local"))
                .andExpect(jsonPath("$.user.language").value("de"))
                .andReturn();
        Cookie session = Arrays.stream(login.getResponse().getCookies())
                .filter(cookie -> cookie.getName().equals("fs_session")).findFirst().orElseThrow();
        assertThat(session.isHttpOnly()).isTrue();

        mvc.perform(get("/api/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(SeedUsers.ORGA.id().toString()))
                .andExpect(jsonPath("$.user.role").value("ORGA"));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'LOGIN' and metadata like ?",
                Integer.class, "%\"DEV\"%")).isEqualTo(1);
    }

    @Test
    void fakeOidcDiscoveryIsServed() throws Exception {
        mvc.perform(get("/dev/oidc/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost/dev/oidc"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost/dev/oidc/jwks"));
    }
}
