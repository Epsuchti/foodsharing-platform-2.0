package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Outside local/e2e (here: the {@code test} profile, like prod without Postgres) no dev bean exists: no dev API, no
 * fake OIDC provider, no captured mails endpoint, no seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProdLikeProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void noDevBeansExist() {
        String[] devBeans = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> {
                    Class<?> type = context.getType(name);
                    return type != null && (type.getName().startsWith("network.foodsharing.dev.")
                            || type.getSimpleName().equals("DevSecurityConfig"));
                })
                .toArray(String[]::new);
        assertThat(devBeans).isEmpty();
        assertThat(context.containsBean("devController")).isFalse();
        assertThat(context.containsBean("fakeOidcController")).isFalse();
    }

    @Test
    void devApiIsNotReachable() throws Exception {
        // D19.1: 404 before security, also for anonymous callers (production does not reveal the dev API)
        mvc.perform(get("/api/dev/users"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/dev/users").with(TestAuth.as(SeedUsers.ORGA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + SeedUsers.ORGA.id() + "\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/dev/clock").with(TestAuth.as(SeedUsers.ORGA))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fakeOidcProviderIsNotReachable() throws Exception {
        mvc.perform(get("/dev/oidc/.well-known/openid-configuration")).andExpect(status().isNotFound());
        mvc.perform(get("/dev/oidc/authorize")).andExpect(status().isNotFound());
        mvc.perform(get("/dev/oidc/jwks")).andExpect(status().isNotFound());
    }

    @Test
    void noSeedDataIsLoaded() {
        assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from history_entry", Integer.class)).isZero();
    }
}
