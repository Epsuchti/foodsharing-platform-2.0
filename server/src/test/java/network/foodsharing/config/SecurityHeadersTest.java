package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A58: CSP without inline scripts, Permissions-Policy, HSTS on HTTPS only, secure cookies in prod. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private network.foodsharing.admin.service.DonationFrameSources frameSources;

    @Test
    void everyResponseCarriesTheSecurityHeaders() throws Exception {
        String csp = mvc.perform(get("/api/public/app-info"))
                .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"))
                .andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).startsWith("default-src 'self'; script-src 'self'").contains("object-src 'none'");
        String scriptSrc = csp.substring(csp.indexOf("script-src"), csp.indexOf(';', csp.indexOf("script-src")));
        assertThat(scriptSrc).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");

        mvc.perform(get("/some/spa/route")).andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void frameSrcAllowsTheConfiguredDonationIframeHosts() throws Exception {
        assertThat(mvc.perform(get("/api/public/app-info")).andReturn().getResponse()
                .getHeader("Content-Security-Policy")).contains("; frame-src 'self';");
        jdbc.update("insert into configuration (id, version, created_at, updated_at, config_key, config_value) "
                + "values (?, 0, current_timestamp, current_timestamp, 'donation.iframe-one-time-url', "
                + "'https://donate.example.org/form?x=1')", java.util.UUID.randomUUID());
        try {
            frameSources.invalidate();
            assertThat(mvc.perform(get("/some/spa/route")).andReturn().getResponse()
                    .getHeader("Content-Security-Policy")).contains("; frame-src 'self' https://donate.example.org;");
        } finally {
            jdbc.update("delete from configuration where config_key = 'donation.iframe-one-time-url'");
            frameSources.invalidate();
        }
    }

    @Test
    void hstsOnlyOnHttps() throws Exception {
        mvc.perform(get("/api/public/app-info").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void prodUsesSecureCookiesAndForwardedHeaders() throws IOException {
        Properties prod = load("application-prod.properties");
        assertThat(prod.getProperty("app.auth.cookie-secure")).isEqualTo("true");
        assertThat(prod.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(load("application.properties").getProperty("server.forward-headers-strategy"))
                .isEqualTo("framework");
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (var in = new ClassPathResource(name).getInputStream()) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
