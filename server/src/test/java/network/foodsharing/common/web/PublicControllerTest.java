package network.foodsharing.common.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import network.foodsharing.config.AppVersionResolver;
import network.foodsharing.testsupport.SecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicController.class)
@Import({SecurityTestConfig.class, AppVersionResolver.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.client-errors.max-per-minute-per-ip=2", "app.auth.passkeys.enabled=false"})
class PublicControllerTest {

    private static final String REPORT = """
            {"message":"TypeError: x is undefined","route":"/dashboard","userAgent":"Firefox","traceId":"abc"}""";

    @Autowired
    private MockMvc mvc;

    @Test
    void appInfoIsPublic() throws Exception {
        mvc.perform(get("/api/public/app-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devMode").value(false))
                .andExpect(jsonPath("$.languages.length()").value(5))
                .andExpect(jsonPath("$.loginMethods.google").value(true))
                .andExpect(jsonPath("$.loginMethods.passkeys").value(false))
                .andExpect(jsonPath("$.serverTime").value("2026-11-02T09:00:00Z"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void clientErrorsAreAcceptedAndRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/public/client-errors").with(csrf()).with(request -> {
                        request.setRemoteAddr("198.51.100.7");
                        return request;
                    }).contentType(MediaType.APPLICATION_JSON).content(REPORT))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/public/client-errors").with(csrf()).with(request -> {
                    request.setRemoteAddr("198.51.100.7");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content(REPORT))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void invalidReportIs400() throws Exception {
        mvc.perform(post("/api/public/client-errors").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
