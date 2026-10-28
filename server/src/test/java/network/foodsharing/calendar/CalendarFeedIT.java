package network.foodsharing.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A11: seeded token, public ICS feed, regenerate and delete. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class CalendarFeedIT {

    private static final String SEED_TOKEN = "0123456789abcdef".repeat(4);

    @Autowired
    private MockMvc mvc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void tokenLifecycleAndPublicFeed() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/calendar/token").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(SEED_TOKEN));
        String feed = mvc.perform(get("/api/public/calendar/" + SEED_TOKEN + ".ics").param("reminders", "30")
                        .param("events", "every").param("history", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(feed).startsWith("BEGIN:VCALENDAR").contains("X-WR-CALNAME:foodsaving");
        mvc.perform(get("/api/public/calendar/" + SEED_TOKEN + ".ics").param("reminders", "x"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/calendar/unknown.ics")).andExpect(status().isNotFound());

        String body = mvc.perform(put("/api/calendar/token").with(csrf()).cookie(foodsaver))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        assertThat(token).hasSize(32).isNotEqualTo(SEED_TOKEN);
        assertThat((String) JsonPath.read(body, "$.url")).endsWith("/api/public/calendar/" + token + ".ics");
        mvc.perform(get("/api/public/calendar/" + SEED_TOKEN + ".ics")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/calendar/" + token + ".ics")).andExpect(status().isOk());
        mvc.perform(delete("/api/calendar/token").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/calendar/token").cookie(foodsaver)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/calendar/" + token + ".ics")).andExpect(status().isNotFound());
    }
}
