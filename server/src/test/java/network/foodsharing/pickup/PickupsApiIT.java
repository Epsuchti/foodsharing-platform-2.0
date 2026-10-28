package network.foodsharing.pickup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import network.foodsharing.testsupport.SeedUsers;
import org.hamcrest.Matchers;
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
 * Pickups on the seed (e2e profile, clock at SEED_NOW 2026-11-02T09:00Z): schedule, eligibility, region rule,
 * sign-up → confirm → removal with bells, single-pickup edits, weekly schedule, concurrent sign-ups, seed counts.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickups-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class PickupsApiIT {

    static final String S = "/api/stores/00000020-0001-4000-8000-0000000000";

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

    private int bells(SeedUsers user, String type) {
        return jdbc.queryForObject("select count(*) from bell where user_id = ? and type = ? and deleted_at is null",
                Integer.class, user.id(), type);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void seedCountsPerUser() {
        String past = "select count(*) from pickup_signup where user_id = ? and pickup_at < timestamp with time zone "
                + "'2026-11-02 09:00:00+00'";
        assertThat(count(past, SeedUsers.FOODSAVER.id())).isEqualTo(24);
        assertThat(count(past, SeedUsers.JUMPER.id())).isEqualTo(6);
        assertThat(count(past, SeedUsers.BIEB.id())).isEqualTo(30);
        assertThat(count(past, SeedUsers.BOT.id())).isEqualTo(10);
        assertThat(count(past, SeedUsers.GUIDE.id())).isEqualTo(12);
        assertThat(count(past, SeedUsers.WELCOME.id())).isEqualTo(8);
        assertThat(count(past + " and pickup_at > timestamp with time zone '2026-09-03 09:00:00+00'",
                SeedUsers.SLEEPY.id())).isZero();
        assertThat(count(past + " and pickup_at > timestamp with time zone '2026-10-26 09:00:00+00'",
                SeedUsers.FOODSAVER.id())).isEqualTo(2);
        assertThat(count("select count(*) from regular_pickup")).isEqualTo(29);
        assertThat(count("select count(distinct store_id) from regular_pickup")).isEqualTo(18);
        // every future sign-up has its pickup row (A20)
        assertThat(count("select count(*) from pickup_signup s where s.pickup_at > timestamp with time zone "
                + "'2026-11-02 09:00:00+00' and not exists (select 1 from pickup p where p.store_id = s.store_id "
                + "and p.pickup_at = s.pickup_at)")).isZero();
    }

    @Test
    void scheduleWithOverridesAndEligibility() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get(S + "01/pickups").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-05T18:00:00Z')].occupiedSlots.length()").value(2))
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-05T18:00:00Z')].eligibility.reasons[0]")
                        .value("ALREADY_SIGNED_UP"))
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-08T09:00:00Z')].description")
                        .value("Sonntags-Extra: Marktreste vom Samstag"))
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-08T09:00:00Z')].isRegular").value(false));
        mvc.perform(get(S + "05/pickups").cookie(login(SeedUsers.BIEB)))
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-09T19:00:00Z')]").isEmpty());
        mvc.perform(get(S + "03/pickups").cookie(foodsaver))
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-03T17:30:00Z')].slotCount").value(5));
        mvc.perform(get(S + "03/pickups/2026-11-10T17:30:00Z/eligibility").cookie(foodsaver))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons", Matchers.hasItem("SIGNUP_NOT_YET_OPEN")));
        Cookie jumper = login(SeedUsers.JUMPER);
        mvc.perform(get(S + "01/pickups").cookie(jumper)).andExpect(status().isForbidden());
        // PU4: a jumper gets the reasons instead of 403; users without relation to the team still get 403
        mvc.perform(get(S + "01/pickups/2026-11-09T18:00:00Z/eligibility").cookie(jumper))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons", Matchers.hasItem("JUMPER_CANNOT_PICKUP")));
        mvc.perform(get(S + "01/pickups/2026-11-09T18:00:00Z/eligibility").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isForbidden());
        mvc.perform(get(S + "01/regular-pickups").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$[0].startTime").value("19:00"));
    }

    @Test
    void regionRuleBitesForTheThirdSignup() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get(S + "01/pickups/2026-11-09T18:00:00Z/eligibility").cookie(foodsaver))
                .andExpect(jsonPath("$.reasons[0]").value("REGION_RULE_TIMESPAN_LIMIT"))
                .andExpect(jsonPath("$.regionRule.active").value(true))
                .andExpect(jsonPath("$.regionRule.pickupsInTimespan").value(2))
                .andExpect(jsonPath("$.regionRule.limitNumber").value(2));
        mvc.perform(post(S + "01/pickups/2026-11-09T18:00:00Z/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PICKUP_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.args.reason").value("REGION_RULE_TIMESPAN_LIMIT"));
    }

    @Test
    void signUpConfirmRemove() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        Cookie welcome = login(SeedUsers.WELCOME);
        String pickup = S + "07/pickups/2026-11-04T19:30:00Z";
        mvc.perform(post(pickup + "/signups").cookie(foodsaver).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed").value(false));
        assertThat(bells(SeedUsers.WELCOME, "PICKUP_SIGNUP_UNCONFIRMED")).isEqualTo(1);
        mvc.perform(post(pickup + "/signups").cookie(foodsaver).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.args.reason").value("ALREADY_SIGNED_UP"));
        String me = "/signups/" + SeedUsers.FOODSAVER.id();
        mvc.perform(post(pickup + me + "/confirm").cookie(foodsaver).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(pickup + me + "/confirm").cookie(welcome).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(pickup + me + "/confirm").cookie(welcome).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CONFIRMED"));
        assertThat(bells(SeedUsers.FOODSAVER, "PICKUP_CONFIRMED")).isEqualTo(1);
        mvc.perform(json(delete(pickup + me).cookie(welcome), "{\"message\":\"Laden geschlossen\",\"sendMessage\":true}"))
                .andExpect(status().isNoContent());
        assertThat(bells(SeedUsers.FOODSAVER, "PICKUP_REMOVED")).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'REMOVED_FROM_SLOT' and reason = ?",
                "Laden geschlossen")).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'SIGN_UP_SLOT' and secondary_subject_id = ?",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        mvc.perform(json(delete(pickup + me).cookie(welcome), "{}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIGNUP_NOT_FOUND"));
        // a manager signs up pre-confirmed; then signs out himself
        mvc.perform(post(pickup + "/signups").cookie(welcome).with(csrf()))
                .andExpect(jsonPath("$.confirmed").value(true));
        mvc.perform(json(delete(pickup + "/signups/" + SeedUsers.WELCOME.id()).cookie(welcome), "{}"))
                .andExpect(status().isNoContent());
        mvc.perform(json(delete(S + "07/pickups/2026-10-28T19:30:00Z/signups/" + SeedUsers.WELCOME.id())
                .cookie(welcome), "{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PICKUP_IN_PAST"));
    }

    @Test
    void editSinglePickupAndWeeklySchedule() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String body = "{\"slotCount\":%d,\"description\":\"%s\"}";
        mvc.perform(json(put(S + "01/pickups/2026-11-05T18:00:00Z").cookie(login(SeedUsers.FOODSAVER)),
                body.formatted(4, ""))).andExpect(status().isForbidden());
        mvc.perform(json(put(S + "01/pickups/2026-11-05T18:00:00Z").cookie(bieb), body.formatted(1, "")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MORE_OCCUPIED_SLOTS"));
        mvc.perform(json(put(S + "01/pickups/2026-10-29T18:00:00Z").cookie(bieb), body.formatted(4, "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PICKUP_IN_PAST"));
        mvc.perform(json(put(S + "01/pickups/2026-11-05T18:00:00Z").cookie(bieb), body.formatted(51, "")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put(S + "01/pickups/2026-11-05T18:00:00Z").cookie(bieb), body.formatted(4, "Mit Velo")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slotCount").value(4))
                .andExpect(jsonPath("$.description").value("Mit Velo"))
                .andExpect(jsonPath("$.isRegular").value(true));
        String s01 = "select count(*) from history_entry where subject_id = '00000020-0001-4000-8000-000000000001' "
                + "and action = ?";
        assertThat(count(s01, "UPDATE_SLOT_COUNT")).isEqualTo(1);
        assertThat(count(s01, "UPDATE_SLOT_DESCRIPTION")).isEqualTo(1);

        String schedule = "[{\"weekday\":\"TUESDAY\",\"startTime\":\"18:00\",\"slotCount\":2},"
                + "{\"weekday\":\"FRIDAY\",\"startTime\":\"18:00\",\"slotCount\":3,\"description\":\"Rampe\"}]";
        mvc.perform(json(put(S + "12/regular-pickups").cookie(bieb), schedule)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(S + "12/pickups").cookie(bieb))
                .andExpect(jsonPath("$[?(@.pickupAt == '2026-11-03T17:00:00Z')].slotCount").value(2));
        mvc.perform(json(put(S + "12/regular-pickups").cookie(bieb),
                "[{\"weekday\":\"TUESDAY\",\"startTime\":\"18:00\",\"slotCount\":2},"
                        + "{\"weekday\":\"TUESDAY\",\"startTime\":\"18:00\",\"slotCount\":3}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_PICKUP_DAY_TIME"));
        assertThat(count("select count(*) from history_entry where action = 'UPDATE_REGULAR_PICKUP_SETTINGS'"))
                .isEqualTo(1);
    }

    @Test
    void concurrentSignupsForTheLastSlot() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        Cookie guide = login(SeedUsers.GUIDE);
        String pickup = S + "09/pickups/2026-11-10T17:00:00Z";
        mvc.perform(json(put(pickup).cookie(bieb), "{\"slotCount\":1}")).andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (Cookie who : List.of(bieb, guide)) {
            Callable<Integer> task = () -> mvc.perform(post(pickup + "/signups").cookie(who).with(csrf()))
                    .andReturn().getResponse().getStatus();
            results.add(pool.submit(task));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("select count(*) from pickup_signup where store_id = '00000020-0001-4000-8000-000000000009' "
                + "and pickup_at = timestamp with time zone '2026-11-10 17:00:00+00'")).isEqualTo(1);
    }
}
