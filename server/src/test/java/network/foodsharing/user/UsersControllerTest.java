package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.api.model.Theme;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryContext;
import network.foodsharing.security.SessionAuthenticator;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.testsupport.WithSeedUser;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.SettingsChange;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.web.UsersController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsersController.class)
@Import({SecurityTestConfig.class, UserMapper.class})
@ActiveProfiles("test")
class UsersControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SessionAuthenticator sessionAuthenticator;

    @Test
    void anonymousCallsNeedAuthentication() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(patch("/api/users/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(userService, never()).updateSettings(any());
    }

    @Test
    @WithSeedUser("foodsaver")
    void returnsTheCurrentUsersProfile() throws Exception {
        when(userService.getCurrent()).thenReturn(foodsaver());

        mvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SeedUsers.FOODSAVER.id().toString()))
                .andExpect(jsonPath("$.email").value("foodsaver@local"))
                .andExpect(jsonPath("$.displayName").value("foodsaver"))
                .andExpect(jsonPath("$.language").value("gws"))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.role").value("FOODSAVER"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.registeredAt").value("2026-11-02T09:00:00Z"));
    }

    @Test
    void theSessionCookieAuthenticatesTheRequest() throws Exception {
        when(sessionAuthenticator.authenticate("cookie-token"))
                .thenReturn(Optional.of(TestAuth.principal(SeedUsers.FOODSAVER)));
        when(userService.getCurrent()).thenReturn(foodsaver());

        mvc.perform(get("/api/users/me").cookie(new Cookie("fs_session", "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("foodsaver@local"));
        mvc.perform(get("/api/users/me").cookie(new Cookie("fs_session", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithSeedUser("foodsaver")
    void aMissingAccountIsUserNotFound() throws Exception {
        when(userService.getCurrent()).thenThrow(ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @WithSeedUser("foodsaver")
    void settingsChangesNeedTheCsrfToken() throws Exception {
        mvc.perform(patch("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(userService, never()).updateSettings(any());
    }

    @Test
    @WithSeedUser("foodsaver")
    void updatesSettingsWithTheReasonInTheHistoryContext() throws Exception {
        AtomicReference<SettingsChange> change = new AtomicReference<>();
        AtomicReference<String> reason = new AtomicReference<>();
        User updated = foodsaver();
        updated.setPreferredLanguage(Language.FR);
        when(userService.updateSettings(any())).thenAnswer(invocation -> {
            change.set(invocation.getArgument(0));
            reason.set(HistoryContext.reason().orElse(null));
            return updated;
        });

        mvc.perform(patch("/api/users/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\",\"reason\":\"moved to Geneva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("fr"));

        assertThat(change.get()).isEqualTo(new SettingsChange(Language.FR, null));
        assertThat(reason.get()).isEqualTo("moved to Geneva");
        assertThat(HistoryContext.reason()).isEmpty();
    }

    @Test
    @WithSeedUser("foodsaver")
    void invalidSettingsAreRejected() throws Exception {
        mvc.perform(patch("/api/users/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"xx\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"reason\":\"" + "r".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        mvc.perform(patch("/api/users/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        verify(userService, never()).updateSettings(any());
    }

    private static User foodsaver() {
        User user = TestUsers.user("foodsaver@local", Role.FOODSAVER);
        user.setId(SeedUsers.FOODSAVER.id());
        user.setPreferredLanguage(Language.GWS);
        user.setTheme(Theme.DARK);
        user.setVerified(true);
        user.setRegisteredAt(FixedClockConfig.SEED_NOW);
        return user;
    }
}
