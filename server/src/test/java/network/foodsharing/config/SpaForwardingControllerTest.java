package network.foodsharing.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        mvc.perform(get("/some/deep/link")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/login")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/login/verify")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/dev/gallery")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void backendPathsAndFilesAreNotForwarded() throws Exception {
        mvc.perform(get("/dev/oidc/authorize")).andExpect(status().isNotFound());
        mvc.perform(get("/login/oauth2/code/google")).andExpect(status().isNotFound());
        mvc.perform(get("/main-ABC123.js")).andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
