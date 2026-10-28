package network.foodsharing.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.WithSeedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpaForwardingController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class SpaForwardingControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void deepLinksAreForwardedToTheAngularApp() throws Exception {
        mvc.perform(get("/some/deep/link")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/").cookie(new Cookie("fs_session", "x")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/login")).andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/login/verify")).andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/dev/gallery")).andExpect(forwardedUrl("/index.csr.html"));
    }

    @Test
    void anonymousLandingGetsThePrerenderedPageInTheirLanguage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(null))
                .andExpect(header().string("Vary", "Accept-Language, Cookie"))
                .andExpect(content().string(containsString("Rette Lebensmittel")))
                .andExpect(content().string(containsString("href=\"http://localhost:4200/en\"")));
        mvc.perform(get("/").header("Accept-Language", "es, zz;q=0.8"))
                .andExpect(content().string(containsString("Rette Lebensmittel")));
    }

    @Test
    void languagePreferenceOrderIsCookieThenAcceptLanguageThenGerman() throws Exception {
        mvc.perform(get("/").header("Accept-Language", "en-GB,de;q=0.5"))
                .andExpect(content().string(containsString("Save food")));
        mvc.perform(get("/").header("Accept-Language", "en").cookie(new Cookie("fs.lang", "de")))
                .andExpect(content().string(containsString("Rette Lebensmittel")));
        mvc.perform(get("/en")).andExpect(content().string(containsString("Save food")));
        mvc.perform(get("/en").cookie(new Cookie("fs_session", "x"))).andExpect(forwardedUrl("/index.csr.html"));
        // a language without a prerendered page (client build skipped) falls back to the shell
        mvc.perform(get("/it")).andExpect(forwardedUrl("/index.csr.html"));
    }

    @Test
    void backendPathsAndFilesAreNotForwarded() throws Exception {
        mvc.perform(get("/dev/oidc/authorize")).andExpect(status().isNotFound());
        mvc.perform(get("/login/oauth2/code/google")).andExpect(status().isNotFound());
        mvc.perform(get("/main-ABC123.js")).andExpect(status().isNotFound());
    }

    @Test
    void frameworkEndpointsAndNestedFilesAreNotForwarded() throws Exception {
        mvc.perform(get("/dev/oidc")).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
        mvc.perform(get("/dev/oidc/.well-known/openid-configuration")).andExpect(forwardedUrl(null));
        mvc.perform(get("/login/webauthn")).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
        mvc.perform(get("/oauth2/authorization/google")).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
        mvc.perform(get("/webauthn/register/options")).andExpect(forwardedUrl(null));
        mvc.perform(get("/h2-console/login.do")).andExpect(forwardedUrl(null));
        mvc.perform(get("/assets/icons/logo.svg")).andExpect(status().isNotFound()).andExpect(forwardedUrl(null));
        mvc.perform(get("/fonts/inter.woff2")).andExpect(forwardedUrl(null));
        mvc.perform(get("/some/deep/file.png"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deepLinksWithSimilarPrefixesAreStillForwarded() throws Exception {
        mvc.perform(get("/dev/oidc-info")).andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/history/me")).andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/account/sessions")).andExpect(forwardedUrl("/index.csr.html"));
        mvc.perform(get("/apiary")).andExpect(forwardedUrl("/index.csr.html"));
    }

    @Test
    void unknownApiPathsNeedAuthentication() throws Exception {
        mvc.perform(get("/api/x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @WithSeedUser
    void unknownApiPathsAreJson404() throws Exception {
        mvc.perform(get("/api/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false));
        mvc.perform(get("/api/x/y/z.json"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
