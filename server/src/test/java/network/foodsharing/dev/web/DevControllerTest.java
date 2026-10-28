package network.foodsharing.dev.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.error.ApiExceptionHandler;
import network.foodsharing.config.DevSecurityConfig;
import network.foodsharing.dev.mapper.DevMapper;
import network.foodsharing.dev.service.DevNotificationService;
import network.foodsharing.dev.service.DevService;
import network.foodsharing.dev.service.DevService.DevLogin;
import network.foodsharing.dev.service.DevService.DevUserView;
import network.foodsharing.dev.service.DevUserCatalog;
import network.foodsharing.notification.mail.CapturedMail;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Every documented dev API response; requests carry no CSRF token and no session (the dev chain is open). */
@WebMvcTest(DevController.class)
@Import({SecurityTestConfig.class, DevSecurityConfig.class, ApiExceptionHandler.class, DevMapper.class,
        AuthMapper.class, DevUserCatalog.class})
@ActiveProfiles({"test", "e2e"})
class DevControllerTest {

    private static final DevUserCatalog CATALOG = new DevUserCatalog();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private network.foodsharing.common.realtime.RealtimePublisher realtimePublisher;

    @MockitoBean
    private DevService devService;

    @MockitoBean
    private DevNotificationService notificationService;

    @MockitoBean
    private network.foodsharing.oauthserver.service.AuthorizationService oauthAuthorization;

    @Test
    void realtimeSessionsAndPing() throws Exception {
        when(realtimePublisher.connectedUsers()).thenReturn(java.util.Set.of(SeedUsers.GUIDE.id()));
        mvc.perform(get("/api/dev/realtime/sessions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.userIds[0]").value(SeedUsers.GUIDE.id().toString()));
        mvc.perform(post("/api/dev/realtime/ping").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json")
                        .content("{\"userId\":\"" + SeedUsers.GUIDE.id() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("PING"));
        org.mockito.Mockito.verify(realtimePublisher).toUsers(org.mockito.ArgumentMatchers.eq(
                java.util.Set.of(SeedUsers.GUIDE.id())), any());
    }

    @Test
    void listDevUsersReturnsSeedUsersWithDescriptionKeys() throws Exception {
        when(devService.listDevUsers()).thenReturn(List.of(view(SeedUsers.NEWBIE), view(SeedUsers.ORGA)));
        mvc.perform(get("/api/dev/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SeedUsers.NEWBIE.id().toString()))
                .andExpect(jsonPath("$[0].email").value("newbie@local"))
                .andExpect(jsonPath("$[0].descriptionKey").value("dev.users.newbie"))
                .andExpect(jsonPath("$[1].role").value("ORGA"));
    }

    @Test
    void loginAsReturnsTheSessionInfo() throws Exception {
        User user = user(SeedUsers.BIEB);
        when(devService.loginAs(eq(SeedUsers.BIEB.id()), any(), any()))
                .thenReturn(new DevLogin(user, TestAuth.principal(SeedUsers.BIEB)));
        mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + SeedUsers.BIEB.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("bieb@local"))
                .andExpect(jsonPath("$.user.role").value("STORE_MANAGER"))
                .andExpect(jsonPath("$.sessionId").value(SeedUsers.TEST_SESSION_ID.toString()));
    }

    @Test
    void loginAsUnknownUserIs404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(devService.loginAs(eq(unknown), any(), any()))
                .thenThrow(ApiException.notFound(ApiErrorCode.DEV_USER_NOT_FOUND));
        mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + unknown + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEV_USER_NOT_FOUND"));
    }

    @Test
    void loginAsWithoutUserIdIs400() throws Exception {
        mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("userId"));
    }

    @Test
    void resetSeedIs204AndRefusesNonH2With409() throws Exception {
        mvc.perform(post("/api/dev/seed/reset")).andExpect(status().isNoContent());
        verify(devService).resetAll();

        doThrow(new ApiException(HttpStatus.CONFLICT, ApiErrorCode.NOT_IMPLEMENTED, "not H2"))
                .when(devService).resetAll();
        mvc.perform(post("/api/dev/seed/reset"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @Test
    void capturedMailsAreListedAndCleared() throws Exception {
        when(devService.capturedMails("newbie@local")).thenReturn(List.of(new CapturedMail(UUID.randomUUID(),
                "newbie@local", "Dein Login-Link", "http://localhost/login/verify?token=t", null,
                List.of("http://localhost/login/verify?token=t"), Instant.parse("2026-11-02T09:00:00Z"), null, List.of())));
        mvc.perform(get("/api/dev/mails").param("to", "newbie@local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subject").value("Dein Login-Link"))
                .andExpect(jsonPath("$[0].links[0]").value("http://localhost/login/verify?token=t"))
                .andExpect(jsonPath("$[0].sentAt").value("2026-11-02T09:00:00Z"));

        mvc.perform(delete("/api/dev/mails")).andExpect(status().isNoContent());
        verify(devService).clearCapturedMails();
    }

    @Test
    void setDevUserRoleReturnsTheUpdatedUser() throws Exception {
        User user = user(SeedUsers.NEWBIE);
        user.setRole(Role.FOODSAVER);
        when(devService.changeRole(SeedUsers.NEWBIE.id(), Role.FOODSAVER))
                .thenReturn(new DevUserView(user, CATALOG.byId(SeedUsers.NEWBIE.id())));
        mvc.perform(put("/api/dev/users/{id}/role", SeedUsers.NEWBIE.id()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"FOODSAVER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FOODSAVER"))
                .andExpect(jsonPath("$.descriptionKey").value("dev.users.newbie"));
    }

    @Test
    void setDevUserRoleErrors() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(devService.changeRole(unknown, Role.ORGA)).thenThrow(ApiException.notFound(ApiErrorCode.DEV_USER_NOT_FOUND));
        mvc.perform(put("/api/dev/users/{id}/role", unknown).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ORGA\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEV_USER_NOT_FOUND"));
        mvc.perform(put("/api/dev/users/{id}/role", unknown).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"KING\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/dev/users/{id}/role", unknown).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void setDevClockFixesAndReleases() throws Exception {
        Instant fixed = Instant.parse("2026-12-24T18:00:00Z");
        when(devService.setClock(fixed)).thenReturn(fixed);
        when(devService.isClockFixed()).thenReturn(true);
        mvc.perform(put("/api/dev/clock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fixedAt\":\"2026-12-24T18:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.now").value("2026-12-24T18:00:00Z"))
                .andExpect(jsonPath("$.fixed").value(true));

        when(devService.setClock(isNull())).thenReturn(Instant.parse("2026-10-27T10:00:00Z"));
        when(devService.isClockFixed()).thenReturn(false);
        mvc.perform(put("/api/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixed").value(false));
    }

    @Test
    void setDevClockWithMalformedInstantIs400() throws Exception {
        mvc.perform(put("/api/dev/clock").contentType(MediaType.APPLICATION_JSON).content("{\"fixedAt\":\"tomorrow\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private static DevUserView view(SeedUsers seed) {
        return new DevUserView(user(seed), CATALOG.byId(seed.id()));
    }

    private static User user(SeedUsers seed) {
        User user = new User();
        user.setId(seed.id());
        user.setEmail(seed.email());
        user.setDisplayName(seed.name());
        user.setRole(seed.role());
        user.setPreferredLanguage(Language.DE);
        return user;
    }
}
