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
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'LOGIN' and metadata like ? "
                + "and subject_id = ?", Integer.class, "%\"DEV\"%", SeedUsers.ORGA.id())).isEqualTo(1);
    }

    @Test
    void regionTreeSeedIsConsistent() {
        assertThat(jdbc.queryForObject("select count(*) from region where type = 'FEDERAL_STATE'", Integer.class))
                .isEqualTo(26);
        assertThat(jdbc.queryForObject("select count(*) from region where type = 'WORKING_GROUP' and parent_id = "
                + "(select id from region where slug = 'zuerich-stadt')", Integer.class)).isEqualTo(16);
        // closure: exactly one row per region and ancestor (computed here from parent_id)
        Map<Object, Object> parents = jdbc.queryForList("select id, parent_id from region").stream()
                .collect(java.util.HashMap::new, (map, row) -> map.put(row.get("ID"), row.get("PARENT_ID")),
                        java.util.HashMap::putAll);
        int expected = 0;
        for (Object id : parents.keySet()) {
            for (Object current = id; current != null; current = parents.get(current)) {
                expected++;
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from region_closure", Integer.class)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from region_closure c join region r on r.id = c.region_id "
                + "where c.depth = 0 and c.ancestor_id <> c.region_id", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select a.slug from region_closure c join region a on a.id = c.ancestor_id "
                + "join region r on r.id = c.region_id where r.slug = 'zuerich-stadt' order by c.depth desc",
                String.class)).containsExactly("europa", "schweiz", "kanton-zuerich", "zuerich-stadt");
        // seed users: 12 members of Zürich Stadt with it as home region; newbie and profile have no region yet
        assertThat(jdbc.queryForObject("select count(*) from region_membership m join region r on r.id = m.region_id "
                + "where r.slug = 'zuerich-stadt' and m.user_id <> ?", Integer.class, SeedUsers.NEWBIE.id())).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from user_account where home_region_id is null and id <> ?",
                Integer.class, SeedUsers.NEWBIE.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select mobile_phone from user_account where id = ?", String.class,
                SeedUsers.FOODSAVER.id())).isEqualTo("+41791234506");
    }

    @Test
    void newbieJoinsZurichAndTheWelcomeTeamGetsABell() throws Exception {
        MvcResult login = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + SeedUsers.NEWBIE.id() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie session = Arrays.stream(login.getResponse().getCookies())
                .filter(cookie -> cookie.getName().equals("fs_session")).findFirst().orElseThrow();
        String zurich = jdbc.queryForObject("select cast(id as varchar) from region where slug = 'zuerich-stadt'",
                String.class);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/regions/" + zurich + "/members/me").cookie(session)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeRegion").value(true));
        mvc.perform(get("/api/regions/mine").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("zuerich-stadt"));
        mvc.perform(get("/api/dev/notifications").param("type", "REGION_NEW_MEMBER")
                        .param("userId", SeedUsers.WELCOME.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identifier").value("REGION_NEW_MEMBER-" + SeedUsers.NEWBIE.id()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/regions/" + zurich + "/members/me").cookie(session)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_LEAVE_HOME_REGION"));
        mvc.perform(post("/api/dev/jobs/region-maintenance/run")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from region_membership where user_id = ?", Integer.class,
                SeedUsers.NEWBIE.id())).isEqualTo(4);
    }

    @Test
    void fakeOidcDiscoveryIsServed() throws Exception {
        mvc.perform(get("/dev/oidc/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost/dev/oidc"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost/dev/oidc/jwks"));
    }
}
