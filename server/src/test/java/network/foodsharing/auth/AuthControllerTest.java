package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.web.AuthController;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.testsupport.WithSeedUser;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AuthController.class)
@Import({SecurityTestConfig.class, AuthMapper.class, HistoryMapper.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final UUID OTHER_SESSION = UUID.fromString("00000002-0001-4000-8000-000000000002");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthSessionService sessionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private HistoryQueryService historyQueryService;

    @Test
    void anonymousSessionIsNotAuthenticated() throws Exception {
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    @WithSeedUser("bieb")
    void signedInSessionCarriesTheUser() throws Exception {
        User user = TestUsers.user("bieb@local", Role.STORE_MANAGER);
        user.setId(SeedUsers.BIEB.id());
        when(userService.findActive(SeedUsers.BIEB.id())).thenReturn(Optional.of(user));

        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.sessionId").value(SeedUsers.TEST_SESSION_ID.toString()))
                .andExpect(jsonPath("$.user.id").value(SeedUsers.BIEB.id().toString()))
                .andExpect(jsonPath("$.user.email").value("bieb@local"))
                .andExpect(jsonPath("$.user.role").value("STORE_MANAGER"))
                .andExpect(jsonPath("$.user.language").value("de"))
                .andExpect(jsonPath("$.user.theme").value("SYSTEM"));
    }

    @Test
    @WithSeedUser("bieb")
    void aSessionOfADisabledOrDeletedUserIsAnonymous() throws Exception {
        when(userService.findActive(SeedUsers.BIEB.id())).thenReturn(Optional.empty());

        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void mutationsNeedTheCsrfToken() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/auth/logout"),
                post("/api/auth/sessions/revoke-others"),
                delete("/api/auth/sessions/" + OTHER_SESSION))) {
            mvc.perform(request.with(TestAuth.as(SeedUsers.FOODSAVER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        mvc.perform(post("/api/auth/sessions/revoke-others").with(csrf().useInvalidToken())
                        .with(TestAuth.as(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        verify(sessionService, never()).logout(any());
        verify(sessionService, never()).revokeOthers();
        verify(sessionService, never()).revoke(any(), any());
    }

    @Test
    void logoutWorksWithoutASession() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
        verify(sessionService).logout(any());
    }

    @Test
    @WithSeedUser("foodsaver")
    void logoutEndsTheSession() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
        verify(sessionService).logout(any());
    }

    @Test
    void sessionManagementNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/auth/sessions/" + OTHER_SESSION).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/sessions/revoke-others").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/security-log"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @WithSeedUser("foodsaver")
    void listsTheOwnSessionsAndMarksTheCurrentOne() throws Exception {
        AuthSession current = session(SeedUsers.TEST_SESSION_ID, "Firefox");
        AuthSession other = session(OTHER_SESSION, "Safari");
        when(sessionService.list(SeedUsers.FOODSAVER.id())).thenReturn(List.of(current, other));

        mvc.perform(get("/api/auth/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SeedUsers.TEST_SESSION_ID.toString()))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].userAgent").value("Firefox"))
                .andExpect(jsonPath("$[0].ipHint").value("abcdef01"))
                .andExpect(jsonPath("$[0].lastSeenAt").value("2026-11-02T09:00:00Z"))
                .andExpect(jsonPath("$[1].current").value(false));
    }

    @Test
    @WithSeedUser("foodsaver")
    void revokingAnotherSessionKeepsTheCookie() throws Exception {
        when(sessionService.revoke(OTHER_SESSION, SessionRevokeReason.REVOKED_BY_USER)).thenReturn(false);

        mvc.perform(delete("/api/auth/sessions/" + OTHER_SESSION).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .noneMatch(cookie -> cookie.startsWith("fs_session=")));
    }

    @Test
    @WithSeedUser("foodsaver")
    void revokingTheCurrentSessionClearsTheCookie() throws Exception {
        when(sessionService.revoke(SeedUsers.TEST_SESSION_ID, SessionRevokeReason.REVOKED_BY_USER)).thenReturn(true);

        mvc.perform(delete("/api/auth/sessions/" + SeedUsers.TEST_SESSION_ID).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getHeaders("Set-Cookie"))
                        .anyMatch(cookie -> cookie.startsWith("fs_session=;") && cookie.contains("Max-Age=0")));
    }

    @Test
    @WithSeedUser("foodsaver")
    void revokingAnUnknownSessionIsNotFound() throws Exception {
        when(sessionService.revoke(OTHER_SESSION, SessionRevokeReason.REVOKED_BY_USER))
                .thenThrow(ApiException.notFound(ApiErrorCode.SESSION_NOT_FOUND));

        mvc.perform(delete("/api/auth/sessions/" + OTHER_SESSION).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        mvc.perform(delete("/api/auth/sessions/not-a-uuid").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithSeedUser("foodsaver")
    void logoutEverywhereRevokesTheOtherSessions() throws Exception {
        when(sessionService.revokeOthers()).thenReturn(3);

        mvc.perform(post("/api/auth/sessions/revoke-others").with(csrf()))
                .andExpect(status().isNoContent());
        verify(sessionService).revokeOthers();
    }

    @Test
    @WithSeedUser("foodsaver")
    @SuppressWarnings("unchecked")
    void securityLogShowsTheUsersAuthHistory() throws Exception {
        var entry = new network.foodsharing.history.domain.HistoryEntry();
        entry.setId(UUID.randomUUID());
        entry.setOccurredAt(FixedClockConfig.SEED_NOW);
        entry.setActorType(network.foodsharing.history.ActorType.SYSTEM);
        entry.setSubjectType(SubjectType.USER);
        entry.setSubjectId(SeedUsers.FOODSAVER.id());
        entry.setAction("LOGIN");
        Page<network.foodsharing.history.domain.HistoryEntry> page = new PageImpl<>(List.of(entry), PageRequest.of(1, 5), 6);
        when(historyQueryService.listForSubject(eq(SubjectType.USER), eq(SeedUsers.FOODSAVER.id()), anyList(), anyInt(),
                anyInt())).thenReturn(page);

        mvc.perform(get("/api/auth/security-log").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.page.totalItems").value(6));

        ArgumentCaptor<List<String>> actions = ArgumentCaptor.forClass(List.class);
        verify(historyQueryService).listForSubject(eq(SubjectType.USER), eq(SeedUsers.FOODSAVER.id()), actions.capture(),
                eq(1), eq(5));
        assertThat(actions.getValue()).contains("LOGIN", "LOGOUT", "LOGIN_FAILED", "SESSION_REVOKED",
                "SESSIONS_REVOKED_ALL", "PASSKEY_REGISTERED").doesNotContain("REGISTRATION_BLOCKED", "FIELD_CHANGED");
    }

    @Test
    @WithSeedUser("foodsaver")
    void loginMethodOperationsAreNotImplementedYet() throws Exception {
        mvc.perform(get("/api/auth/methods"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
        mvc.perform(post("/api/auth/magic-link").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.ch\"}"))
                .andExpect(status().isNotImplemented());
    }

    private static AuthSession session(UUID id, String userAgent) {
        AuthSession session = new AuthSession();
        session.setId(id);
        session.setCreatedAt(Instant.parse("2026-10-01T08:00:00Z"));
        session.setLastSeenAt(FixedClockConfig.SEED_NOW);
        session.setExpiresAt(FixedClockConfig.SEED_NOW.plusSeconds(3600));
        session.setUserAgent(userAgent);
        session.setIpHash("abcdef01" + "0".repeat(56));
        return session;
    }
}
