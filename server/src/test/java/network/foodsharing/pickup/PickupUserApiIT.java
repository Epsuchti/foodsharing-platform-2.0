package network.foodsharing.pickup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * User-side pickup views on the seed (B5): my pickups, past pickups, options, agenda, store history, sign-out of all
 * future pickups, reminder setting, region member last pickup, region statistics.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickup-user-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class PickupUserApiIT {

    static final String ZURICH = "00000010-0001-4000-8000-000000000040";
    static final String S01 = "/api/stores/00000020-0001-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void myPastAndAgenda() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/users/me/pickups").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pickupAt").value("2026-11-04T17:30:00Z"))
                .andExpect(jsonPath("$[0].store.timeZone").value("Europe/Zurich"))
                .andExpect(jsonPath("$[1].confirmed").value(false))
                .andExpect(jsonPath("$[1].occupiedSlots.length()").value(2));
        String past = "/api/users/" + SeedUsers.FOODSAVER.id() + "/pickups/past";
        mvc.perform(get(past).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalItems").value(24))
                .andExpect(jsonPath("$.items[0].pickupAt").value("2026-10-29T18:00:00Z"));
        mvc.perform(get(past).cookie(login(SeedUsers.JUMPER))).andExpect(status().isForbidden());
        mvc.perform(get(past).cookie(login(SeedUsers.ORGA))).andExpect(status().isOk());
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/agenda/2026-11-05").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pickups.length()").value(1))
                .andExpect(jsonPath("$.introPickups.length()").value(0));
    }

    @Test
    void optionsAndHistory() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/users/me/pickups/options").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-04T19:30:00Z')].store.name").isNotEmpty())
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-07T17:00:00Z')]").isEmpty())
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-05T18:00:00Z')].isConfirmed").value(false));
        mvc.perform(get("/api/users/me/pickups/options").cookie(login(SeedUsers.QUIZ)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_VERIFIED"));
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get(S01 + "/pickup-history").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pickupAt").value("2026-10-29T18:00:00Z"))
                .andExpect(jsonPath("$.items[0].isAvailable").value(false));
        mvc.perform(get(S01 + "/pickup-history").param("from", "2025-01-01T00:00:00Z")
                .param("to", "2026-11-01T00:00:00Z").cookie(bieb))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RANGE_TOO_LARGE"));
        mvc.perform(get(S01 + "/pickup-history").cookie(foodsaver)).andExpect(status().isForbidden());
    }

    @Test
    void memberLastPickupAndRemindersSetting() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZURICH + "/members").param("size", "100").cookie(bot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.FOODSAVER.id() + "')].lastPickupAt")
                        .value("2026-10-29T18:00:00Z"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(patch("/api/users/me/settings").cookie(foodsaver), "{\"pickupRemindersEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pickupRemindersEnabled").value(false));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'PICKUP_REMINDERS_CHANGED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void signOutOfAllFuturePickups() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(json(delete("/api/users/" + SeedUsers.BOT.id() + "/pickups").cookie(login(SeedUsers.JUMPER)),
                "{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(json(delete("/api/users/" + SeedUsers.BOT.id() + "/pickups").cookie(bot),
                "{\"reason\":\"Ferien\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/pickups").cookie(bot)).andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'ALL_FUTURE_PICKUPS_DELETED' "
                + "and secondary_subject_id = ?", Integer.class, SeedUsers.BOT.id())).isPositive();
    }
}
