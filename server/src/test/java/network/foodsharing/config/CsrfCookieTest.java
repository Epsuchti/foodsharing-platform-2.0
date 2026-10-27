package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.web.AuthController;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import network.foodsharing.auth.service.AuthMethodsService;
import network.foodsharing.auth.service.MagicLinkService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The real CSRF round trip of docs/conventions.md A9 (XSRF-TOKEN cookie readable by the SPA, echoed in
 * X-XSRF-TOKEN). Uses a fresh context and never Spring Security Test's {@code csrf()} post processor, which
 * replaces the filter's token repository for the whole cached context.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityTestConfig.class, AuthMapper.class, HistoryMapper.class})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthSessionService sessionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private HistoryQueryService historyQueryService;

    @MockitoBean
    private AuthMethodsService authMethodsService;

    @MockitoBean
    private MagicLinkService magicLinkService;

    @Test
    void theSessionBootstrapHandsOutAReadableCsrfCookieThatMutationsMustEcho() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().path("XSRF-TOKEN", "/"))
                .andReturn();
        Cookie token = bootstrap.getResponse().getCookie("XSRF-TOKEN");
        assertThat(token.getValue()).isNotBlank();

        mvc.perform(post("/api/auth/logout").cookie(token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/auth/logout").cookie(token).header("X-XSRF-TOKEN", "forged"))
                .andExpect(status().isForbidden());
        verify(sessionService, never()).logout(any());

        mvc.perform(post("/api/auth/logout").cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isNoContent());
        verify(sessionService).logout(any());
    }
}
