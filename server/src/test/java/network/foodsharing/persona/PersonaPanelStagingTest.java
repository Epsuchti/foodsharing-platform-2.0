package network.foodsharing.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.StagingNoIndexFilter;
import network.foodsharing.notification.mail.service.MailRecipientPolicy;
import network.foodsharing.persona.service.PersonaPanelService;
import network.foodsharing.persona.web.PersonaController;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Decision 173: the staging profile on top of its base profile ({@code test} here instead of {@code prod}, which
 * needs the server's environment variables; StagingProfileGuard accepts either base, listed first) with a panel
 * password: seed data, persona panel, no dev API, noindex, demo banner, staging mail policy.
 */
@SpringBootTest(properties = "app.staging.persona-panel.password=foobar")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "staging"})
class PersonaPanelStagingTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppProperties properties;

    @Test
    void rightPasswordListsTheFourteenPersonasInJourneyOrder() throws Exception {
        mvc.perform(list("{\"password\":\"foobar\"}", "10.1.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SeedUsers.values().length))
                .andExpect(jsonPath("$[0].id").value(SeedUsers.NEWBIE.id().toString()))
                .andExpect(jsonPath("$[0].email").value("newbie@local"))
                .andExpect(jsonPath("$[0].descriptionKey").value("dev.users.newbie"))
                .andExpect(jsonPath("$[13].id").value(SeedUsers.ORGA.id().toString()))
                .andExpect(jsonPath("$[13].role").value("ORGA"));
    }

    @Test
    void wrongOrMissingPasswordIs401() throws Exception {
        mvc.perform(list("{\"password\":\"foobaz\"}", "10.1.0.2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PERSONA_PANEL_PASSWORD_INVALID"));
        mvc.perform(list("{}", "10.1.0.2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PERSONA_PANEL_PASSWORD_INVALID"));
        mvc.perform(login("{\"userId\":\"" + SeedUsers.ORGA.id() + "\",\"password\":\"nope\"}", "10.1.0.2"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("fs_session"));
    }

    @Test
    void eleventhFailureFromOneIpIs429() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(list("{\"password\":\"wrong-" + i + "\"}", "10.1.0.3")).andExpect(status().isUnauthorized());
        }
        mvc.perform(list("{\"password\":\"wrong\"}", "10.1.0.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // blocked even with the right password; other IPs still work
        mvc.perform(list("{\"password\":\"foobar\"}", "10.1.0.3")).andExpect(status().isTooManyRequests());
        mvc.perform(list("{\"password\":\"foobar\"}", "10.1.0.4")).andExpect(status().isOk());
    }

    @Test
    void loginAsOrgaCreatesADevSession() throws Exception {
        mvc.perform(login("{\"userId\":\"" + SeedUsers.ORGA.id() + "\",\"password\":\"foobar\"}", "10.1.0.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("orga@local"))
                .andExpect(jsonPath("$.user.role").value("ORGA"))
                .andExpect(cookie().exists("fs_session"));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'LOGIN' and subject_id = ? "
                + "and metadata like '%\"method\":\"DEV\"%'", Integer.class, SeedUsers.ORGA.id())).isPositive();
    }

    @Test
    void onlySeedPersonasCanBeLoggedInto() throws Exception {
        User fresh = users.save(TestUsers.user("fresh-" + UUID.randomUUID() + "@local"));
        mvc.perform(login("{\"userId\":\"" + fresh.getId() + "\",\"password\":\"foobar\"}", "10.1.0.6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSONA_NOT_FOUND"))
                .andExpect(cookie().doesNotExist("fs_session"));
        mvc.perform(login("{\"userId\":\"" + UUID.randomUUID() + "\",\"password\":\"foobar\"}", "10.1.0.6"))
                .andExpect(status().isNotFound());

        // a persona whose account a tester moved to a real address is no longer offered
        UUID newbie = SeedUsers.NEWBIE.id();
        jdbc.update("update user_account set email = ? where id = ?", "newbie-real@example.org", newbie);
        try {
            mvc.perform(login("{\"userId\":\"" + newbie + "\",\"password\":\"foobar\"}", "10.1.0.6"))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("update user_account set email = ? where id = ?", SeedUsers.NEWBIE.email(), newbie);
        }
    }

    @Test
    void devApiAndFakeOidcStayHidden() throws Exception {
        mvc.perform(get("/api/dev/users")).andExpect(status().isNotFound());
        mvc.perform(post("/api/dev/login-as").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + SeedUsers.ORGA.id() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/dev/oidc/authorize")).andExpect(status().isNotFound());
        String[] devBeans = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> {
                    Class<?> type = context.getType(name);
                    return type != null && (type.getName().startsWith("network.foodsharing.dev.")
                            || type.getSimpleName().equals("DevSecurityConfig"));
                })
                .toArray(String[]::new);
        assertThat(devBeans).isEmpty();
        assertThat(context.getBeanNamesForType(PersonaController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(PersonaPanelService.class)).hasSize(1);
    }

    @Test
    void appInfoNoindexSeedAndMailPolicy() throws Exception {
        mvc.perform(get("/api/public/app-info"))
                .andExpect(status().isOk())
                .andExpect(header().string(StagingNoIndexFilter.HEADER, StagingNoIndexFilter.VALUE))
                .andExpect(jsonPath("$.devMode").value(false))
                .andExpect(jsonPath("$.personaPanel").value(true))
                .andExpect(jsonPath("$.demoBanner").value(true));
        assertThat(properties.getMail().getRecipientPolicy()).isEqualTo(MailRecipientPolicy.STAGING);
        assertThat(properties.getTelegram().getMode()).isEqualTo("log");
        assertThat(jdbc.queryForObject("select count(*) from user_account where id = ?", Integer.class,
                SeedUsers.ORGA.id())).isOne();
        // the password never reaches the client
        String body = mvc.perform(get("/api/public/app-info")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("foobar");
    }

    private static MockHttpServletRequestBuilder list(String body, String ip) {
        return post("/api/personas/list").with(csrf()).with(remoteAddr(ip))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder login(String body, String ip) {
        return post("/api/personas/login").with(csrf()).with(remoteAddr(ip))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
