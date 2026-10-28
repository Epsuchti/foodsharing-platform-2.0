package network.foodsharing.dev.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.error.ApiExceptionHandler;
import network.foodsharing.config.DevSecurityConfig;
import network.foodsharing.dev.mapper.DevMapper;
import network.foodsharing.dev.service.DevNotificationService;
import network.foodsharing.dev.service.DevNotificationService.DevBell;
import network.foodsharing.dev.service.DevService;
import network.foodsharing.dev.service.DevUserCatalog;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** M2 dev endpoints: persisted notifications, running jobs by name, the iCal fixture. */
@WebMvcTest(DevController.class)
@Import({SecurityTestConfig.class, DevSecurityConfig.class, ApiExceptionHandler.class, DevMapper.class,
        AuthMapper.class, DevUserCatalog.class})
@ActiveProfiles({"test", "e2e"})
class DevNotificationsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DevService devService;

    @MockitoBean
    private DevNotificationService notificationService;

    @Test
    void notificationsListBellsAndTheirMails() throws Exception {
        Bell bell = new Bell();
        bell.setId(UUID.randomUUID());
        bell.setUserId(SeedUsers.WELCOME.id());
        bell.setType(NotificationType.REGION_NEW_MEMBER);
        bell.setTitleKey("bell.REGION_NEW_MEMBER");
        bell.setLink("/users/" + SeedUsers.NEWBIE.id());
        bell.setIdentifier("REGION_NEW_MEMBER-" + SeedUsers.NEWBIE.id());
        bell.setMailOutboxId(UUID.randomUUID());
        bell.setCreatedAt(Instant.parse("2026-11-02T09:00:00Z"));
        when(notificationService.notifications(SeedUsers.WELCOME.id(), "REGION_NEW_MEMBER"))
                .thenReturn(List.of(new DevBell(bell, Map.of("regionName", "Zürich Stadt"))));

        mvc.perform(get("/api/dev/notifications").param("userId", SeedUsers.WELCOME.id().toString())
                        .param("type", "REGION_NEW_MEMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].channel").value("BELL"))
                .andExpect(jsonPath("$[0].type").value("REGION_NEW_MEMBER"))
                .andExpect(jsonPath("$[0].params.regionName").value("Zürich Stadt"))
                .andExpect(jsonPath("$[0].identifier").value("REGION_NEW_MEMBER-" + SeedUsers.NEWBIE.id()))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-02T09:00:00Z"))
                .andExpect(jsonPath("$[1].channel").value("MAIL"))
                .andExpect(jsonPath("$[1].id").value(bell.getMailOutboxId().toString()));
    }

    @Test
    void jobsRunByNameAndUnknownJobsAre404() throws Exception {
        mvc.perform(post("/api/dev/jobs/region-maintenance/run")).andExpect(status().isNoContent());
        verify(notificationService).runJob("region-maintenance");

        doThrow(ApiException.notFound(ApiErrorCode.DEV_JOB_NOT_FOUND)).when(notificationService).runJob("nope");
        mvc.perform(post("/api/dev/jobs/nope/run"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEV_JOB_NOT_FOUND"));
    }

    @Test
    void icalFixtureIsServedAsCalendar() throws Exception {
        when(notificationService.icalFixture()).thenReturn("BEGIN:VCALENDAR\nEND:VCALENDAR\n");
        mvc.perform(get("/api/dev/fixtures/ical"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string("BEGIN:VCALENDAR\nEND:VCALENDAR\n"));
    }

    @Test
    void theRealFixtureIsAValidCalendar() {
        String ics = new DevNotificationService(null, List.of(), null).icalFixture();
        org.assertj.core.api.Assertions.assertThat(ics).startsWith("BEGIN:VCALENDAR").contains("BEGIN:VEVENT")
                .contains("END:VCALENDAR");
    }
}
