package network.foodsharing.intropickup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
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

/** EinAbs end to end on the seed (e2e profile, clock at SEED_NOW): booking, guide, admin, import, public, iCal. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:intro-pickup-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class IntroPickupApiIT {

    private static final String ZURICH = "00000010-0001-4000-8000-000000000040";

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

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    @Test
    void newcomersSeeWhyTheyMayNotBookAndBookCancel() throws Exception {
        Cookie einab1 = login(SeedUsers.EINAB1);
        String list = body(get("/api/regions/" + ZURICH + "/intro-pickups/bookable").param("size", "50")
                .cookie(einab1));
        // 1 DONE + 1 BOOKED with both Zürich guides: only the ONLINE EinAb (ignored category) stays bookable
        List<Boolean> bookable = JsonPath.read(list, "$.items[?(@.category != 'ONLINE')].bookable");
        assertThat(bookable).isNotEmpty().doesNotContain(true);
        List<Boolean> online = JsonPath.read(list, "$.items[?(@.category == 'ONLINE')].bookable");
        assertThat(online).containsExactly(true);

        Cookie quiz = login(SeedUsers.QUIZ);
        mvc.perform(get("/api/regions/" + ZURICH + "/intro-pickups/bookable").param("category", "GROSSABHOLUNG")
                        .cookie(quiz))
                .andExpect(jsonPath("$.items[0].bookable").value(false))
                .andExpect(jsonPath("$.items[0].notBookableReason").value("MINIMUM_PICKUP_COUNT_NOT_REACHED"));
        String markets = body(get("/api/regions/" + ZURICH + "/intro-pickups/bookable").param("category", "MARKET")
                .cookie(quiz));
        assertThat(JsonPath.<Integer>read(markets, "$.page.totalItems")).isEqualTo(2);
        String slotId = JsonPath.read(markets, "$.items[0].slotId");
        String pickupId = JsonPath.read(markets, "$.items[0].id");
        String secondSlot = JsonPath.read(markets, "$.items[1].slotId");

        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(json(post("/api/intro-pickups/slots/" + slotId + "/book").cookie(guide), ""))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GUIDES_CANNOT_BOOK"));

        mvc.perform(json(post("/api/intro-pickups/slots/" + slotId + "/book").cookie(quiz), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.introPickup.privateAddress").isNotEmpty());
        mvc.perform(json(post("/api/intro-pickups/slots/" + slotId + "/book").cookie(quiz), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
        mvc.perform(json(post("/api/intro-pickups/slots/" + secondSlot + "/book").cookie(quiz), ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(Matchers.oneOf("USER_ALREADY_BOOKED_WITH_GUIDE",
                        "USER_ALREADY_BOOKED_IN_CATEGORY")));
        mvc.perform(get("/api/users/me/intro-pickups").cookie(quiz))
                .andExpect(jsonPath("$[?(@.slotId == '" + slotId + "')].status").value("BOOKED"));
        mvc.perform(get("/api/intro-pickups/" + pickupId + "/calendar.ics").cookie(quiz))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/calendar")))
                .andExpect(content().string(Matchers.containsString("BEGIN:VEVENT")));
        mvc.perform(get("/api/intro-pickups/" + pickupId + "/calendar.ics").cookie(einab1))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'INTRO_PICKUP_BOOKED'",
                Integer.class, SeedUsers.QUIZ.id())).isEqualTo(1);

        mvc.perform(delete("/api/intro-pickups/slots/" + slotId + "/booking").with(csrf()).cookie(einab1))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ONLY_OWN_BOOKINGS_CANCELLABLE"));
        mvc.perform(delete("/api/intro-pickups/slots/" + slotId + "/booking").with(csrf()).cookie(quiz))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/intro-pickups").cookie(quiz)).andExpect(jsonPath("$").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'BOOKING_CANCELLED_BY_USER' "
                + "and secondary_subject_id = ?", Integer.class, SeedUsers.QUIZ.id())).isEqualTo(1);
    }

    @Test
    void guidesManageTheirEinAbs() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                        "{\"category\":\"BAKERY\",\"startAt\":\"2026-10-01T17:00:00Z\",\"slotCount\":1,"
                                + "\"visitFairteiler\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INTRO_PICKUP_START_IN_PAST"));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                        "{\"category\":\"BAKERY\",\"startAt\":\"2026-11-20T17:00:00Z\",\"slotCount\":2,"
                                + "\"visitFairteiler\":false,\"privateAddress\":\"Hinterhof 2\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLIC_LOCATION_REQUIRED"));
        String created = body(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                "{\"category\":\"BAKERY\",\"startAt\":\"2026-11-20T17:00:00Z\",\"slotCount\":2,"
                        + "\"visitFairteiler\":false,\"privateAddress\":\"Hinterhof 2\",\"publicGeneralLocation\":"
                        + "\"Kreis 3\",\"businessName\":\"Beck Wiedikon\",\"privateInfo\":\"Hintereingang\","
                        + "\"savePrivateInfoAsTemplate\":true}"));
        String id = JsonPath.read(created, "$.id");
        assertThat(JsonPath.<List<?>>read(created, "$.slots")).hasSize(2);
        mvc.perform(json(patch("/api/intro-pickups/" + id).cookie(guide), "{\"slotCount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_COUNT_REDUCTION_NOT_SUPPORTED"));
        mvc.perform(json(patch("/api/intro-pickups/" + id).cookie(guide), "{\"slotCount\":3,\"publicInfo\":\"Neu\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.publicInfo").value("Neu"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(patch("/api/intro-pickups/" + id).cookie(foodsaver), "{\"publicInfo\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONLY_OWN_INTRO_PICKUPS_MANAGEABLE"));
        mvc.perform(get("/api/guide/private-info-templates").cookie(guide))
                .andExpect(jsonPath("$[?(@.category == 'BAKERY')].content").value("Hintereingang"));
        mvc.perform(json(put("/api/intro-pickups/" + id + "/guide").cookie(guide),
                        "{\"guideId\":\"" + SeedUsers.FOODSAVER.id() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ASSIGNABLE_GUIDE_NOT_FOUND"));
        mvc.perform(delete("/api/intro-pickups/" + id).with(csrf()).cookie(guide)).andExpect(status().isNoContent());
        mvc.perform(get("/api/intro-pickups/" + id).cookie(guide)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("select action from history_entry where subject_id = ? order by occurred_at",
                String.class, java.util.UUID.fromString(id))).contains("CREATED", "UPDATED", "DELETED");

        // the cancelled past booking of sleepy is counted again by the guide, then not counted by an admin
        String past = body(get("/api/regions/" + ZURICH + "/guide/bookings").param("past", "true").param("size", "50")
                .cookie(guide));
        List<String> cancelled = JsonPath.read(past, "$.items[?(@.status == 'CANCELLED')].slotId");
        assertThat(cancelled).hasSize(1);
        mvc.perform(json(post("/api/intro-pickups/slots/" + cancelled.getFirst() + "/count").cookie(guide), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.counted").value(true));
        mvc.perform(json(post("/api/intro-pickups/slots/" + cancelled.getFirst() + "/done").cookie(guide), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(json(put("/api/intro-pickups/slots/" + cancelled.getFirst() + "/counted").cookie(bot),
                        "{\"counted\":false,\"reason\":\"No-show\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(json(post("/api/intro-pickups/slots/" + cancelled.getFirst() + "/comments").cookie(guide),
                        "{\"comment\":\"Nachgetragen\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/intro-pickups/slots/" + cancelled.getFirst() + "/comments").cookie(bot))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void adminsSeeTheFunnelGuidesAndImportPastEinAbs() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZURICH + "/newcomer-funnel").cookie(bot))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetIntroPickups").value(3))
                .andExpect(jsonPath("$.fairteilerRequired").value(true))
                .andExpect(jsonPath("$.steps[0].stage").value("REGISTERED"))
                .andExpect(jsonPath("$.steps[?(@.stage == 'COMPLETE')].users[0].user.displayName").isNotEmpty());
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(get("/api/regions/" + ZURICH + "/newcomer-funnel").cookie(guide))
                .andExpect(status().isOk()).andExpect(jsonPath("$.steps[0].users").isEmpty());
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZURICH + "/newcomer-funnel").cookie(foodsaver))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guides").cookie(bot))
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.GUIDE.id() + "')].active").value(true));
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.FOODSAVER.id() + "/enabled")
                        .cookie(bot), "{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/guide/regions").cookie(foodsaver)).andExpect(jsonPath("$[0].id").value(ZURICH));
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.FOODSAVER.id() + "/enabled")
                        .cookie(bot), "{\"enabled\":false,\"reason\":\"Pause\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.status").value("DISABLED"));
        // EA17: disabled guides stay in the list with their status
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guides").cookie(bot))
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.FOODSAVER.id() + "')].status")
                        .value("DISABLED"))
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.GUIDE.id() + "')].status")
                        .value("ACTIVE"));
        // EA17: region administrators grant / revoke admin rights (CAN_MANAGE_USERS) of a guide
        String guideAdmin = "/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.GUIDE.id() + "/admin";
        mvc.perform(json(put(guideAdmin).cookie(foodsaver), "{\"admin\":true}")).andExpect(status().isForbidden());
        mvc.perform(json(put(guideAdmin).cookie(bot), "{\"admin\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canManageUsers").value(true));
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guides").cookie(guide)).andExpect(status().isOk());
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.FOODSAVER.id() + "/admin")
                .cookie(guide), "{\"admin\":true}")).andExpect(status().isForbidden());
        mvc.perform(json(put(guideAdmin).cookie(bot), "{\"admin\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canManageUsers").value(false));
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guides").cookie(guide)).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + java.util.UUID.randomUUID() + "/admin")
                .cookie(bot), "{\"admin\":true}")).andExpect(status().isNotFound());

        String csv = "﻿ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback\n"
                + "100004;01.09.2026;1;babus;Bäckerei;Anna;nein;\"Gut\ngemacht\"\n"
                + "100004;01.09.2026;1;Babus Bakery;Bäckerei;Anna;nein;doppelt\n"
                + "100099;02.09.2026;1;Coop;Supermarkt;Anna;ja;x\n"
                + "100005;03.12.2026;x;Coop;Supermarkt;Anna;ja;Zukunft\n"
                + "100005;05.09.2026;x;Coop;FT-Reinigung;Anna;ja;Nicht erschienen\n";
        String request = "{\"csv\":" + new tools.jackson.databind.ObjectMapper().writeValueAsString(csv) + "}";
        mvc.perform(json(post("/api/regions/" + ZURICH + "/admin/past-import/preview").cookie(foodsaver), request))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/regions/" + ZURICH + "/admin/past-import/preview").cookie(bot), request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.totalRows").value(5))
                .andExpect(jsonPath("$.counts.willCreate").value(2))
                .andExpect(jsonPath("$.counts.willSkip").value(1))
                .andExpect(jsonPath("$.counts.needsAttention").value(2))
                .andExpect(jsonPath("$.rows[0].store").value("Babu's Bakery"))
                .andExpect(jsonPath("$.rows[0].reasons").value(Matchers.hasItem("STORE_NAME_CORRECTED")))
                .andExpect(jsonPath("$.rows[1].reasons").value(Matchers.hasItem("DUPLICATE_IN_FILE")))
                .andExpect(jsonPath("$.rows[2].reasons").value(Matchers.hasItem("UNKNOWN_USER")))
                .andExpect(jsonPath("$.rows[3].reasons").value(Matchers.hasItem("FUTURE_DATE")))
                .andExpect(jsonPath("$.rows[4].reasons").value(Matchers.hasItems("NOT_COUNTED", "CATEGORY_MAPPED")));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/admin/past-import/confirm").cookie(bot), request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/admin/past-import/confirm").cookie(bot), request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.skipped").value(3));
        assertThat(jdbc.queryForObject("select count(*) from intro_pickup where imported = true and region_id = ? "
                + "and business_name = 'Babu''s Bakery' and start_at = ?", Integer.class, java.util.UUID.fromString(ZURICH),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-09-01T10:00:00Z"))))
                .isEqualTo(1);

        mvc.perform(json(post("/api/regions/" + ZURICH + "/admin/store-aliases").cookie(bot),
                        "{\"aliasKey\":\"Babus\",\"storeName\":\"X\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_ALIAS_EXISTS"));
        mvc.perform(json(post("/api/regions/" + ZURICH + "/admin/store-aliases").cookie(bot),
                        "{\"aliasKey\":\"Coop Stauffi\",\"storeName\":\"Coop Stauffacher\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aliasKey").value("coopstauffi"));
    }

    @Test
    void publicListFeedAndSubscriptions() throws Exception {
        mvc.perform(get("/api/public/regions/zuerich-stadt/intro-pickups").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].regionSlug").value("zuerich-stadt"))
                .andExpect(jsonPath("$.items[0].privateAddress").doesNotExist());
        mvc.perform(get("/api/public/regions/nowhere/intro-pickups")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/intro-pickups/ical/5eedf00dc0ffee0000000000000000000000000000000010.ics"))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("BEGIN:VEVENT")));
        mvc.perform(get("/api/public/intro-pickups/ical/unknown")).andExpect(status().isNotFound());
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(get("/api/guide/ical-feed-link").cookie(guide))
                .andExpect(jsonPath("$.url").value(Matchers.endsWith(
                        "/api/public/intro-pickups/ical/5eedf00dc0ffee0000000000000000000000000000000010.ics")));
        mvc.perform(json(patch("/api/guide/profile").cookie(guide), "{\"icalLink\":\"https://evil.example/x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ICAL_LINK_NOT_ALLOWED"));

        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(json(put("/api/users/me/intro-pickup-subscription").cookie(newbie),
                        "{\"regionId\":\"" + ZURICH + "\",\"channel\":\"IN_APP\",\"language\":\"de\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_REGION_MEMBER"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(put("/api/users/me/intro-pickup-subscription").cookie(foodsaver),
                        "{\"regionId\":\"" + ZURICH + "\",\"channel\":\"IN_APP\",\"language\":\"de\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_NEEDED_VERIFIED"));
        Cookie quiz = login(SeedUsers.QUIZ);
        mvc.perform(get("/api/users/me/intro-pickup-subscription").cookie(quiz))
                .andExpect(jsonPath("$.channel").value("EMAIL")).andExpect(jsonPath("$.active").value(true));
        mvc.perform(json(post("/api/public/intro-pickups/unsubscribe"), "{\"token\":\"nope\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_UNSUBSCRIBE_TOKEN"));
        mvc.perform(json(post("/api/public/intro-pickups/unsubscribe"), "{\"token\":\"seed-unsubscribe-quiz\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me/intro-pickup-subscription").cookie(quiz))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(json(put("/api/users/me/intro-pickup-subscription").cookie(quiz),
                        "{\"regionId\":\"" + ZURICH + "\",\"channel\":\"IN_APP\",\"language\":\"fr\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.language").value("fr"));
        assertThat(jdbc.queryForObject("select unsubscribe_token from intro_pickup_subscription where user_id = ?",
                String.class, SeedUsers.QUIZ.id())).isNotEqualTo("seed-unsubscribe-quiz");

        // a new EinAb tells the subscribed newcomer
        Integer bellsBefore = jdbc.queryForObject("select count(*) from bell where user_id = ? and type = "
                + "'INTRO_PICKUP_NEW_BOOKABLE'", Integer.class, SeedUsers.QUIZ.id());
        String created = body(json(post("/api/regions/" + ZURICH + "/guide/intro-pickups").cookie(guide),
                "{\"category\":\"RESTAURANT\",\"startAt\":\"2026-11-25T17:00:00Z\",\"slotCount\":1,"
                        + "\"visitFairteiler\":true,\"privateAddress\":\"Gasse 1\",\"publicGeneralLocation\":"
                        + "\"Altstadt\",\"businessName\":\"Zum Löwen\"}"));
        assertThat(JsonPath.<String>read(created, "$.id")).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = "
                + "'INTRO_PICKUP_NEW_BOOKABLE'", Integer.class, SeedUsers.QUIZ.id())).isEqualTo(bellsBefore + 1);
    }
}
