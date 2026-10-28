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
import network.foodsharing.user.service.ProfileChange;
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
    private network.foodsharing.user.service.UserAccountService accountService;

    @MockitoBean
    private network.foodsharing.user.service.EmailChangePort emailChangePort;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SessionAuthenticator sessionAuthenticator;

    @MockitoBean
    private network.foodsharing.user.service.UserSearchService userSearch;

    @Test
    @WithSeedUser("foodsaver")
    void searchUsersValidatesTheQueryAndMapsSummaries() throws Exception {
        network.foodsharing.user.domain.User found = network.foodsharing.testsupport.TestUsers.user("nina@test.local",
                network.foodsharing.api.model.Role.STORE_MANAGER);
        found.setId(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(userSearch.search("ni", null, 10)).thenReturn(java.util.List.of(found));
        mvc.perform(get("/api/users/search").param("q", "ni")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("nina"));
        mvc.perform(get("/api/users/search").param("q", "n")).andExpect(status().isBadRequest());
    }

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

    @Test
    @WithSeedUser("foodsaver")
    void updatesTheOwnProfile() throws Exception {
        AtomicReference<ProfileChange> change = new AtomicReference<>();
        User updated = foodsaver();
        updated.setMobilePhone("+41791234567");
        updated.setGender(network.foodsharing.user.domain.Gender.FEMALE);
        updated.setFoodsaverNumber(100006L);
        when(userService.updateMyProfile(any())).thenAnswer(invocation -> {
            change.set(invocation.getArgument(0));
            return updated;
        });

        mvc.perform(patch("/api/users/me/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Sarah\",\"mobilePhone\":\"079 123 45 67\",\"gender\":\"FEMALE\","
                                + "\"contactVisibility\":\"PUBLIC\",\"dateOfBirth\":\"1988-11-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobilePhone").value("+41791234567"))
                .andExpect(jsonPath("$.phoneValid").value(true))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.foodsaverNumber").value(100006));

        assertThat(change.get().firstName()).isEqualTo("Sarah");
        assertThat(change.get().mobilePhone()).isEqualTo("079 123 45 67");
        assertThat(change.get().contactVisibility()).isEqualTo(network.foodsharing.user.domain.ContactVisibility.PUBLIC);
        assertThat(change.get().dateOfBirth()).isEqualTo(java.time.LocalDate.of(1988, 11, 30));
    }

    @Test
    @WithSeedUser("foodsaver")
    void invalidProfileUpdatesAre400() throws Exception {
        when(userService.updateMyProfile(any())).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_PHONE_NUMBER,
                java.util.List.of(new network.foodsharing.common.error.ApiFieldError("mobilePhone", "PhoneNumber", "x")),
                java.util.Map.of()));
        mvc.perform(patch("/api/users/me/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobilePhone\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("mobilePhone"));
        mvc.perform(patch("/api/users/me/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayName"));
        mvc.perform(patch("/api/users/me/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gender\":\"ROBOT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/me/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithSeedUser("bot")
    void userSummaryShowsThePhoneOnlyWhenAllowed() throws Exception {
        User user = foodsaver();
        user.setMobilePhone("+41791234506");
        when(userService.requireActive(SeedUsers.FOODSAVER.id())).thenReturn(user);
        when(userService.mayCurrentUserSeeContact(user)).thenReturn(false);
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("foodsaver"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.phone").doesNotExist());
        when(userService.mayCurrentUserSeeContact(user)).thenReturn(true);
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id()))
                .andExpect(jsonPath("$.phone").value("+41791234506"));

        when(userService.requireActive(SeedUsers.NEWBIE.id())).thenThrow(ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        mvc.perform(get("/api/users/" + SeedUsers.NEWBIE.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
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
