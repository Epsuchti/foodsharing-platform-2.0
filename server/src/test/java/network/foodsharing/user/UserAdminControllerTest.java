package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.testsupport.WithSeedUser;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.ProfileChange;
import network.foodsharing.user.service.UserAdminService;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.web.UserAdminController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Every documented response of the admin-users tag (AD1, A4 admin edit, D10.3 profile change history). */
@WebMvcTest(UserAdminController.class)
@Import({SecurityTestConfig.class, UserMapper.class, HistoryMapper.class})
@ActiveProfiles("test")
class UserAdminControllerTest {

    private static final String BASE = "/api/admin/users/" + SeedUsers.NEWBIE.id();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private network.foodsharing.user.service.UserAccountService accountService;

    @MockitoBean
    private network.foodsharing.user.service.EmailChangePort emailChangePort;

    @MockitoBean
    private network.foodsharing.user.service.AccountDeletionService accountDeletionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAdminService adminService;

    @MockitoBean
    private HistoryQueryService historyQueryService;

    @MockitoBean
    private network.foodsharing.history.ActorDirectory actorDirectory;

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get(BASE + "/profile")).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/active").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false,\"reason\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithSeedUser("bot")
    void adminEditPassesTheReasonAndMapsTheProfile() throws Exception {
        User newbie = newbie();
        newbie.setFirstName("Lena");
        ArgumentCaptor<ProfileChange> change = ArgumentCaptor.forClass(ProfileChange.class);
        when(userService.updateProfileAsAdmin(eq(SeedUsers.NEWBIE.id()), change.capture(), eq("typo")))
                .thenReturn(newbie);

        mvc.perform(patch(BASE + "/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Lena\",\"gender\":\"FEMALE\",\"reason\":\"typo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lena"))
                .andExpect(jsonPath("$.active").value(true));
        assertThat(change.getValue().firstName()).isEqualTo("Lena");
        assertThat(change.getValue().gender()).isEqualTo(network.foodsharing.user.domain.Gender.FEMALE);
        assertThat(change.getValue().displayName()).isNull();
    }

    @Test
    @WithSeedUser("bot")
    void adminEditWithoutReasonIs400AndServiceErrorsAreMapped() throws Exception {
        mvc.perform(patch(BASE + "/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Lena\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        verify(userService, never()).updateProfileAsAdmin(any(), any(), any());

        when(userService.updateProfileAsAdmin(any(), any(), any()))
                .thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED))
                .thenThrow(ApiException.notFound(ApiErrorCode.USER_NOT_FOUND))
                .thenThrow(ApiException.conflict(ApiErrorCode.NOT_A_MEMBER));
        String body = "{\"homeRegionId\":\"" + SeedUsers.ORGA.id() + "\",\"reason\":\"moved\"}";
        mvc.perform(patch(BASE + "/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch(BASE + "/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(patch(BASE + "/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    }

    @Test
    @WithSeedUser("orga")
    void enableAndDisable() throws Exception {
        User newbie = newbie();
        newbie.setActive(false);
        when(userService.setActive(SeedUsers.NEWBIE.id(), false, "spam")).thenReturn(newbie);
        mvc.perform(put(BASE + "/active").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"reason\":\"spam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        when(userService.setActive(SeedUsers.ORGA.id(), false, "oops"))
                .thenThrow(ApiException.conflict(ApiErrorCode.CANNOT_DEACTIVATE_SELF));
        mvc.perform(put("/api/admin/users/" + SeedUsers.ORGA.id() + "/active").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false,\"reason\":\"oops\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
        mvc.perform(put(BASE + "/active").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithSeedUser("bot")
    void profileForAdminsAnd403() throws Exception {
        User newbie = newbie();
        newbie.setNoAutomaticDelete(true);
        newbie.setEmailBouncedAt(java.time.Instant.parse("2026-11-01T08:00:00Z"));
        newbie.setDeletedAt(java.time.Instant.parse("2026-11-02T09:00:00Z"));
        newbie.setDeleteReason("asked by user");
        when(adminService.getForAdmin(SeedUsers.NEWBIE.id())).thenReturn(newbie);
        mvc.perform(get(BASE + "/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newbie@local"))
                .andExpect(jsonPath("$.emailBouncedAt").value("2026-11-01T08:00:00Z"))
                .andExpect(jsonPath("$.noAutomaticDelete").value(true))
                .andExpect(jsonPath("$.deletedAt").value("2026-11-02T09:00:00Z"))
                .andExpect(jsonPath("$.deleteReason").value("asked by user"));
        when(adminService.getForAdmin(SeedUsers.ORGA.id())).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        mvc.perform(get("/api/admin/users/" + SeedUsers.ORGA.id() + "/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithSeedUser("bot")
    void changeHistoryDefaultsToProfileActions() throws Exception {
        when(historyQueryService.list(any())).thenReturn(Page.empty());
        mvc.perform(get(BASE + "/change-history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page.totalItems").value(0));
        ArgumentCaptor<HistoryFilter> filter = ArgumentCaptor.forClass(HistoryFilter.class);
        verify(historyQueryService).list(filter.capture());
        assertThat(filter.getValue().subjectType()).isEqualTo(SubjectType.USER);
        assertThat(filter.getValue().subjectId()).isEqualTo(SeedUsers.NEWBIE.id());
        assertThat(filter.getValue().actions()).contains("PROFILE_UPDATED_BY_ADMIN", "HOME_REGION_CHANGED")
                .doesNotContain("LOGIN");

        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(adminService)
                .requireReadableUser(SeedUsers.ORGA.id());
        mvc.perform(get("/api/admin/users/" + SeedUsers.ORGA.id() + "/change-history").param("actions", "LOGIN"))
                .andExpect(status().isForbidden());
    }

    private static User newbie() {
        User user = TestUsers.user("newbie@local", Role.FOODSHARER);
        user.setId(SeedUsers.NEWBIE.id());
        return user;
    }
}
