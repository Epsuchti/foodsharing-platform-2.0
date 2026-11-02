package network.foodsharing.fsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FS2 (decision 148) on the seed (clock 2026-11-02T09:00Z): Wiedikon's shifts, sign-up → manager bell → confirm →
 * bell, removal → bell, history rows, the derived team, the weekly editor, the region's cleaning Fairteiler and the
 * D7 link removal when cleaning is switched off.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fsp-cleaning-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FspCleaningFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String WIEDIKON = "00000049-0001-4000-8000-000000000001";
    private static final String LANGSTRASSE = "00000049-0001-4000-8000-000000000002";
    private static final String BASE = "/api/food-share-points/" + WIEDIKON + "/cleaning";
    /** Saturday 10:00 Zürich, a regular occurrence without sign-ups. */
    private static final String FREE_SATURDAY = "2026-11-14T09:00:00Z";
    /** The seeded unconfirmed sign-up of guide. */
    private static final String GUIDE_SHIFT = "2026-11-07T09:00:00Z";

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

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int history(String action) {
        return count("select count(*) from history_entry where subject_id = ? and action = ?",
                UUID.fromString(WIEDIKON), action);
    }

    private int bells(SeedUsers user, String type) {
        return count("select count(*) from bell where user_id = ? and type = ? and deleted_at is null", user.id(),
                type);
    }

    /** Decision 154: the person's own shifts, the agenda, the profile number and the calendar feed. */
    @Test
    @Order(0)
    void ownShiftsAgendaAndProfileCount() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(get("/api/users/me/cleaning-shifts").cookie(guide)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fsp.id").value(WIEDIKON))
                .andExpect(jsonPath("$[0].shiftAt").value(GUIDE_SHIFT))
                .andExpect(jsonPath("$[0].confirmed").value(false));
        mvc.perform(get("/api/users/me/cleaning-shifts").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/users/me/cleaning-shifts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/" + SeedUsers.GUIDE.id() + "/agenda/2026-11-07").cookie(guide))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cleaningShifts[0].shiftAt").value(GUIDE_SHIFT));
        mvc.perform(get("/api/users/" + SeedUsers.GUIDE.id() + "/agenda/2026-11-08").cookie(guide))
                .andExpect(jsonPath("$.cleaningShifts").isEmpty());
        // only confirmed shifts in the past count: guide's is upcoming and unconfirmed, foodsaver has one from October
        mvc.perform(get("/api/users/" + SeedUsers.GUIDE.id() + "/profile").cookie(guide))
                .andExpect(jsonPath("$.stats.cleaningShiftCount").value(0));
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/profile").cookie(guide))
                .andExpect(jsonPath("$.stats.cleaningShiftCount").value(1));
    }

    @Test
    @Order(1)
    void seedShiftsTeamAndEligibility() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/food-share-points/" + WIEDIKON).cookie(foodsaver))
                .andExpect(jsonPath("$.cleaningEnabled").value(true));
        mvc.perform(get(BASE + "/shifts").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shiftAt").value(GUIDE_SHIFT))
                .andExpect(jsonPath("$[0].occupiedSlots[0].user.id").value(SeedUsers.GUIDE.id().toString()))
                .andExpect(jsonPath("$[0].occupiedSlots[0].confirmed").value(false))
                .andExpect(jsonPath("$[0].eligibility.eligible").value(true))
                .andExpect(jsonPath("$[1].shiftAt").value("2026-11-11T17:00:00Z"))
                .andExpect(jsonPath("$[1].isRegular").value(false))
                .andExpect(jsonPath("$[?(@.shiftAt == '2026-11-28T09:00:00Z')]").isEmpty());
        mvc.perform(get(BASE + "/regular-shifts").cookie(foodsaver))
                .andExpect(jsonPath("$[0].weekday").value("SATURDAY")).andExpect(jsonPath("$[0].startTime")
                        .value("10:00"));
        mvc.perform(get(BASE + "/team").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShifts").value(2))
                .andExpect(jsonPath("$.members[0].user.id").value(SeedUsers.BIEB.id().toString()))
                .andExpect(jsonPath("$.members[0].manager").value(true))
                .andExpect(jsonPath("$.members[?(@.user.id == '" + SeedUsers.BOT.id() + "')].shiftCount")
                        .value(org.hamcrest.Matchers.contains(0)));
        mvc.perform(get(BASE + "/shifts").cookie(login(SeedUsers.SLEEPY)))
                .andExpect(jsonPath("$[0].eligibility.reasons[0]").value("SLEEPING"));
        mvc.perform(post(BASE + "/shifts/" + FREE_SATURDAY + "/signups").with(csrf()).cookie(login(SeedUsers.PENDING)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLEANING_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.args.reason").value("NOT_VERIFIED"));
        mvc.perform(get("/api/food-share-points/" + LANGSTRASSE + "/cleaning/shifts").cookie(foodsaver))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLEANING_NOT_ENABLED"));
    }

    @Test
    @Order(2)
    void signUpConfirmRemoveWithBellsAndHistory() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(post(BASE + "/shifts/" + FREE_SATURDAY + "/signups").with(csrf()).cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmed").value(false));
        assertThat(count("select count(*) from fsp_cleaning_shift where fsp_id = ? and shift_at = ?",
                UUID.fromString(WIEDIKON), java.sql.Timestamp.from(java.time.Instant.parse(FREE_SATURDAY))))
                .isEqualTo(1);
        assertThat(bells(SeedUsers.BIEB, "FSP_CLEANING_SIGNUP_UNCONFIRMED")).isEqualTo(1);
        assertThat(history("CLEANING_SIGNED_UP")).isEqualTo(1);
        mvc.perform(post(BASE + "/shifts/" + FREE_SATURDAY + "/signups").with(csrf()).cookie(foodsaver))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.args.reason").value("ALREADY_SIGNED_UP"));

        String confirm = BASE + "/shifts/" + FREE_SATURDAY + "/signups/" + SeedUsers.FOODSAVER.id() + "/confirm";
        mvc.perform(post(confirm).with(csrf()).cookie(login(SeedUsers.JUMPER))).andExpect(status().isForbidden());
        mvc.perform(post(confirm).with(csrf()).cookie(bieb)).andExpect(status().isNoContent());
        mvc.perform(post(confirm).with(csrf()).cookie(bieb)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CONFIRMED"));
        assertThat(bells(SeedUsers.FOODSAVER, "FSP_CLEANING_CONFIRMED")).isEqualTo(1);
        assertThat(history("CLEANING_CONFIRMED")).isEqualTo(1);

        // the manager signs up pre-confirmed
        mvc.perform(post(BASE + "/shifts/" + FREE_SATURDAY + "/signups").with(csrf()).cookie(bieb))
                .andExpect(jsonPath("$.confirmed").value(true));
        mvc.perform(post(BASE + "/shifts/" + FREE_SATURDAY + "/signups").with(csrf()).cookie(login(SeedUsers.BOT)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.args.reason").value("NO_FREE_SLOT"));

        String guide = BASE + "/shifts/" + GUIDE_SHIFT + "/signups/" + SeedUsers.GUIDE.id();
        mvc.perform(delete(guide).with(csrf()).cookie(foodsaver)).andExpect(status().isForbidden());
        mvc.perform(delete(guide).with(csrf()).cookie(bieb)).andExpect(status().isNoContent());
        mvc.perform(delete(guide).with(csrf()).cookie(bieb)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIGNUP_NOT_FOUND"));
        assertThat(bells(SeedUsers.GUIDE, "FSP_CLEANING_REMOVED")).isEqualTo(1);
        assertThat(history("CLEANING_REMOVED")).isEqualTo(1);
        // no unconfirmed future sign-up left: the managers' bell is gone
        assertThat(bells(SeedUsers.BIEB, "FSP_CLEANING_SIGNUP_UNCONFIRMED")).isZero();

        mvc.perform(delete(BASE + "/shifts/" + FREE_SATURDAY + "/signups/" + SeedUsers.FOODSAVER.id()).with(csrf())
                .cookie(foodsaver)).andExpect(status().isNoContent());
        assertThat(history("CLEANING_SIGNED_OUT")).isEqualTo(1);
    }

    @Test
    @Order(3)
    void managersEditTheSchedule() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String body = "[{\"weekday\":\"SATURDAY\",\"startTime\":\"10:00\",\"slotCount\":2},"
                + "{\"weekday\":\"WEDNESDAY\",\"startTime\":\"18:30\",\"slotCount\":1,\"description\":\"Abends\"}]";
        mvc.perform(put(BASE + "/regular-shifts").with(csrf()).cookie(login(SeedUsers.FOODSAVER))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/regular-shifts").with(csrf()).cookie(bieb).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(put(BASE + "/regular-shifts").with(csrf()).cookie(bieb).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"weekday\":\"SATURDAY\",\"startTime\":\"10:00\",\"slotCount\":2},"
                        + "{\"weekday\":\"SATURDAY\",\"startTime\":\"10:00\",\"slotCount\":1}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_PICKUP_DAY_TIME"));
        mvc.perform(patch(BASE + "/shifts/2026-11-21T09:00:00Z").with(csrf()).cookie(bieb)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slotCount\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slotCount").value(0));
        mvc.perform(patch(BASE + "/shifts/2026-10-01T09:00:00Z").with(csrf()).cookie(bieb)
                .contentType(MediaType.APPLICATION_JSON).content("{\"slotCount\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PICKUP_IN_PAST"));
        assertThat(history("CLEANING_SCHEDULE_CHANGED")).isEqualTo(2);
    }

    @Test
    @Order(4)
    void regionCleaningFairteilerAndTheD7Removal() throws Exception {
        String url = "/api/regions/" + ZUERICH + "/cleaning-food-share-points";
        mvc.perform(get(url).cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get(url).cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.selected[0].id").value(WIEDIKON))
                .andExpect(jsonPath("$.candidates.length()").value(1));
        mvc.perform(put(url).with(csrf()).cookie(orga).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fspIds\":[\"" + LANGSTRASSE + "\"]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FSP_NOT_CLEANING_CANDIDATE"))
                .andExpect(jsonPath("$.args.fspId").value(LANGSTRASSE));
        mvc.perform(put(url).with(csrf()).cookie(orga).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fspIds\":[]}")).andExpect(status().isOk()).andExpect(jsonPath("$.selected").isEmpty());
        mvc.perform(put(url).with(csrf()).cookie(orga).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fspIds\":[\"" + WIEDIKON + "\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.selected[0].name").value("Fairteiler Wiedikon"));
        assertThat(history("REGION_CLEANING_FSPS_CHANGED")).isEqualTo(2);

        // D7: switching cleaning off removes the link (with history) and closes the tab
        mvc.perform(patch("/api/food-share-points/" + WIEDIKON).with(csrf()).cookie(login(SeedUsers.BIEB))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cleaningEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cleaningEnabled").value(false));
        assertThat(count("select count(*) from region_cleaning_fsp where fsp_id = ?", UUID.fromString(WIEDIKON)))
                .isZero();
        assertThat(history("REGION_CLEANING_FSPS_CHANGED")).isEqualTo(3);
        assertThat(history("CLEANING_DISABLED")).isEqualTo(1);
        mvc.perform(get(BASE + "/shifts").cookie(orga)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLEANING_NOT_ENABLED"));
    }
}
