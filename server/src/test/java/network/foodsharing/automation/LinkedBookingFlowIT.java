package network.foodsharing.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.testsupport.SeedUsers;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Decision 167 S3 end to end on the seed: S-04 links S-01 one-way (seed link, set by ORGA to the same day up to 2 days
 * after, rules switched on). foodsaver (member of both) books the S-04 pickup together with an S-01 one in one request;
 * a full linked pickup rolls both back; the S-01 sign-out leaves S-04 "Verknüpfung fehlt" with a bell until the
 * deadline, a new S-01 pickup clears it, and after the deadline the evaluate job frees S-04. Now = SEED_NOW (Monday
 * 2026-11-02 10:00 Zurich).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:linked-booking-flow-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LinkedBookingFlowIT {

    static final UUID S01_ID = UUID.fromString("00000020-0001-4000-8000-000000000001");
    static final UUID S04_ID = UUID.fromString("00000020-0001-4000-8000-000000000004");
    static final String S01 = "/api/stores/" + S01_ID;
    static final String S04 = "/api/stores/" + S04_ID;
    /** X: S-04 Wednesday 11.11. 18:30; Y: S-01 Thursday 12.11. 19:00 (the day after). */
    static final String X = "2026-11-11T17:30:00Z";
    static final String Y = "2026-11-12T18:00:00Z";
    /** S-04 Saturday 7.11. 19:30 and S-01 Monday 9.11. 19:00. */
    static final String X2 = "2026-11-07T18:30:00Z";
    static final String Y2 = "2026-11-09T18:00:00Z";

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

    private int signups(UUID storeId, String at) {
        return count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ?",
                storeId, SeedUsers.FOODSAVER.id(), Timestamp.from(Instant.parse(at)));
    }

    private int pending(String at) {
        return count("select count(*) from pickup_link_pending where store_id = ? and user_id = ? and pickup_at = ?",
                S04_ID, SeedUsers.FOODSAVER.id(), Timestamp.from(Instant.parse(at)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
    }

    private static String linked(String storeId, String at) {
        return "{\"linkedPickups\":[{\"storeId\":\"" + storeId + "\",\"pickupAt\":\"" + at + "\"}]}";
    }

    @Test
    @Order(1)
    void aMissingLinkedPickupBlocksAndTheOptionsOfferTheLinkedStore() throws Exception {
        // the Zürich pickup rule (2 per 7 days) would count every booking of this test
        jdbc.update("update store set use_region_pickup_rule = false where id in (?, ?)", S01_ID, S04_ID);
        mvc.perform(json(put(S04 + "/automation/pickup-policy").cookie(login(SeedUsers.ORGA)), """
                {"enabled":true,"gapRuleEnabled":false,"minimumGapDays":0,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":false,"publicHolidayRuleEnabled":false,"excludedDates":[],
                 "linkedStores":[{"storeIds":["%s"],"bidirectional":false,"windowMode":"LAST_DAYS",
                 "windowDaysBefore":0,"windowDaysAfter":2,"graceMinutes":120}]}""".formatted(S01_ID)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedStores[0].graceMinutes").value(120));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get(S04 + "/pickups").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pickupAt == '" + X + "')].eligibility.ruleBlocks[0].rule")
                        .value(Matchers.contains("LINKED_STORE")));
        // a pickup that only misses its linked pickup stays offered on /pickups (the dialog books both)
        mvc.perform(get("/api/users/me/pickups/options").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.store.id == '" + S04_ID + "' && @.pickupAt == '" + X + "')]")
                        .isNotEmpty());
        mvc.perform(get(S04 + "/automation/linked-options").param("pickupAt", X).cookie(foodsaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].stores[0].id").value(S01_ID.toString()))
                .andExpect(jsonPath("$.rules[0].windowMode").value("LAST_DAYS"))
                .andExpect(jsonPath("$.rules[0].after").value(2))
                .andExpect(jsonPath("$.rules[0].satisfiedBy").doesNotExist())
                .andExpect(jsonPath("$.rules[0].notMember").isEmpty())
                .andExpect(jsonPath("$.rules[0].options[?(@.pickupAt == '" + Y + "')].freeCount")
                        .value(Matchers.contains(3)));
        // store managers are never blocked: no step for them
        mvc.perform(get(S04 + "/automation/linked-options").param("pickupAt", X).cookie(login(SeedUsers.WELCOME)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules").isEmpty());
        mvc.perform(post(S04 + "/pickups/" + X + "/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LINKED_PICKUP_REQUIRED"))
                .andExpect(jsonPath("$.args.storeIds").value(S01_ID.toString()));
        assertThat(signups(S04_ID, X)).isZero();
    }

    @Test
    @Order(2)
    void bothPickupsAreBookedInOneRequest() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(post(S04 + "/pickups/" + X + "/signups").cookie(foodsaver), linked(S01_ID.toString(), Y)))
                .andExpect(status().isOk());
        assertThat(signups(S04_ID, X)).isEqualTo(1);
        assertThat(signups(S01_ID, Y)).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'SIGN_UP_SLOT' and subject_id in (?, ?) "
                + "and secondary_subject_id = ?", S04_ID, S01_ID, SeedUsers.FOODSAVER.id())).isEqualTo(2);
        mvc.perform(get(S04 + "/automation/linked-options").param("pickupAt", X).cookie(foodsaver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].satisfiedBy.store.id").value(S01_ID.toString()))
                .andExpect(jsonPath("$.rules[0].satisfiedBy.pickupAt").value(Y))
                .andExpect(jsonPath("$.rules[0].options").isEmpty());
    }

    @Test
    @Order(3)
    void aFullOrInvalidLinkedPickupRollsEverythingBack() throws Exception {
        // S-01 Monday 9.11. (seed: sleepy and bieb) shrinks to two slots: full
        mvc.perform(json(put(S01 + "/pickups/" + Y2).cookie(login(SeedUsers.BIEB)), "{\"slotCount\":2}"))
                .andExpect(status().is2xxSuccessful());
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(post(S04 + "/pickups/" + X2 + "/signups").cookie(foodsaver), linked(S01_ID.toString(), Y2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LINKED_PICKUP_FULL"))
                .andExpect(jsonPath("$.args.storeId").value(S01_ID.toString()))
                .andExpect(jsonPath("$.args.pickupAt").value(Y2));
        assertThat(signups(S04_ID, X2)).isZero();
        // a time without a pickup there: blocked, nothing stored
        mvc.perform(json(post(S04 + "/pickups/" + X2 + "/signups").cookie(foodsaver),
                        linked(S01_ID.toString(), "2026-11-08T17:30:00Z")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LINKED_PICKUP_BLOCKED"))
                .andExpect(jsonPath("$.args.reason").value("NO_FREE_SLOT"));
        assertThat(signups(S04_ID, X2)).isZero();
        // the pickup itself as its own linked pickup, or more than five
        mvc.perform(json(post(S04 + "/pickups/" + X2 + "/signups").cookie(foodsaver), linked(S04_ID.toString(), X2)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void signingOutTheLinkedPickupLeavesTheSlotWaitingWithABell() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(delete(S01 + "/pickups/" + Y + "/signups/" + SeedUsers.FOODSAVER.id()).cookie(foodsaver)
                .with(csrf())).andExpect(status().is2xxSuccessful());
        assertThat(pending(X)).isEqualTo(1);
        assertThat(count("select count(*) from pickup_link_pending where store_id = ? and user_id = ? "
                + "and deadline_at = ?", S04_ID, SeedUsers.FOODSAVER.id(),
                Timestamp.from(Instant.parse("2026-11-02T11:00:00Z")))).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'LINK_MISSING' and secondary_subject_id = ?",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'PICKUP_LINK_MISSING'",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        // the own slot shows until when; the store manager sees it, too
        String slot = "$[?(@.pickupAt == '" + X + "')].occupiedSlots[?(@.user.id == '" + SeedUsers.FOODSAVER.id()
                + "')].linkMissingUntil";
        mvc.perform(get(S04 + "/pickups").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath(slot).value(Matchers.contains("2026-11-02T11:00:00Z")));
        mvc.perform(get(S04 + "/pickups").cookie(login(SeedUsers.WELCOME))).andExpect(status().isOk())
                .andExpect(jsonPath(slot).value(Matchers.contains("2026-11-02T11:00:00Z")));
        // within the deadline the evaluate job keeps the slot
        mvc.perform(post("/api/admin/jobs/automation-evaluate/run").cookie(login(SeedUsers.ORGA)).with(csrf()))
                .andExpect(status().is2xxSuccessful());
        assertThat(signups(S04_ID, X)).isEqualTo(1);
        // booking another S-01 pickup in the window (the repair step) clears it
        mvc.perform(post(S01 + "/pickups/" + Y + "/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isOk());
        assertThat(pending(X)).isZero();
    }

    @Test
    @Order(5)
    void afterTheDeadlineTheJobFreesTheSlot() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(delete(S01 + "/pickups/" + Y + "/signups/" + SeedUsers.FOODSAVER.id()).cookie(foodsaver)
                .with(csrf())).andExpect(status().is2xxSuccessful());
        assertThat(pending(X)).isEqualTo(1);
        try {
            mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2026-11-02T11:30:00Z\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/admin/jobs/automation-evaluate/run").cookie(login(SeedUsers.ORGA)).with(csrf()))
                    .andExpect(status().is2xxSuccessful());
        } finally {
            mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2026-11-02T09:00:00Z\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(signups(S04_ID, X)).isZero();
        assertThat(pending(X)).isZero();
        assertThat(count("select count(*) from pickup_policy_audit where store_id = ? and user_id = ? and pickup_at = ? "
                + "and decision = 'DECLINED' and reasons like '%LINKED_STORE%'", S04_ID, SeedUsers.FOODSAVER.id(),
                Timestamp.from(Instant.parse(X)))).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'PICKUP_AUTO_DECLINED'",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
    }
}
