package network.foodsharing.notification.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.push.service.PushSubscriptionService;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationSettingsService;
import network.foodsharing.security.SessionAuthenticator;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.WithSeedUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** N3: bells API mapping, own user only, 401 when logged out, BELL_NOT_FOUND. */
@WebMvcTest(BellsController.class)
@Import(SecurityTestConfig.class)
@ActiveProfiles("test")
class BellsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BellService bells;

    @MockitoBean
    private NotificationSettingsService settings;

    @MockitoBean
    private PushSubscriptionService pushSubscriptions;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private AppProperties properties;

    @MockitoBean
    private SessionAuthenticator sessionAuthenticator;

    @Test
    void loggedOutIs401() throws Exception {
        mvc.perform(get("/api/bells")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithSeedUser("foodsaver")
    void listMapsBellsOfTheCurrentUser() throws Exception {
        Bell bell = new Bell();
        bell.setId(UUID.randomUUID());
        bell.setType(NotificationType.PICKUP_CONFIRMED);
        bell.setTitleKey("bell.PICKUP_CONFIRMED");
        bell.setCreatedAt(Instant.parse("2026-11-02T08:00:00Z"));
        bell.setPushedAt(Instant.parse("2026-11-02T08:00:01Z"));
        when(bells.page(eq(SeedUsers.FOODSAVER.id()), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(bell), PageRequest.of(0, 20), 1));
        when(bells.params(bell)).thenReturn(Map.of("storeName", "Coop"));

        mvc.perform(get("/api/bells")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("PICKUP_CONFIRMED"))
                .andExpect(jsonPath("$.items[0].params.storeName").value("Coop"))
                .andExpect(jsonPath("$.items[0].icon").value("calendar-check"))
                .andExpect(jsonPath("$.items[0].pushed").value(true))
                .andExpect(jsonPath("$.items[0].mailed").value(false))
                .andExpect(jsonPath("$.page.totalItems").value(1));
    }

    @Test
    @WithSeedUser("foodsaver")
    void readAndDelete() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/bells/read").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"]}")).andExpect(status().isNoContent());
        verify(bells).markRead(SeedUsers.FOODSAVER.id(), List.of(id));
        doThrow(ApiException.notFound(ApiErrorCode.BELL_NOT_FOUND)).when(bells).delete(any(), eq(id));
        mvc.perform(delete("/api/bells/" + id).with(csrf())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BELL_NOT_FOUND"));
        mvc.perform(post("/api/bells/read").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }
}
