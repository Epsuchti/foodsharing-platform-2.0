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
import java.util.Arrays;
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

/** M4 B4 end to end on the seed: S-01 policy decides at sign-up time; contract operations of B4. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pickup-policy-flow-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PickupPolicyFlowIT {

    static final String S = "/api/stores/00000020-0001-4000-8000-0000000000";
    static final String S01 = S + "01";
    static final String S01_ID = "00000020-0001-4000-8000-000000000001";
    static final String ZUERICH = "/api/regions/00000010-0001-4000-8000-000000000040";

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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
    }

    @Test
    @Order(0)
    void evaluateJobLeavesTheSeededS01SignupsAlone() throws Exception {
        mvc.perform(post("/api/admin/jobs/automation-evaluate/run").cookie(login(SeedUsers.ORGA)).with(csrf()))
                .andExpect(status().is2xxSuccessful());
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ? "
                + "and confirmed = false", java.util.UUID.fromString(S01_ID), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-05T18:00:00Z")))).isEqualTo(1);
    }

    @Test
    @Order(1)
    void signupIsConfirmedInTheSameRequestCycle() throws Exception {
        // the Zürich pickup rule (2 per 7 days) would block foodsaver's sign-ups within the 14-day window
        jdbc.update("update store set use_region_pickup_rule = false where id = ?", java.util.UUID.fromString(S01_ID));
        // the seed keeps S-01 in dry run (M3 scenarios use its unconfirmed sign-ups); go live for this flow
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(login(SeedUsers.BIEB)), """
                {"enabled":true,"dryRun":false,"gapRuleEnabled":true,"minimumGapDays":2,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":true,"publicHolidayRuleEnabled":true,"linkedStores":[],
                 "excludedDates":["2026-11-08"]}""")).andExpect(status().isOk());
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(post(S01 + "/pickups/2026-11-12T18:00:00Z/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ? "
                + "and confirmed = true", java.util.UUID.fromString(S01_ID), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T18:00:00Z")))).isEqualTo(1);
        assertThat(count("select count(*) from pickup_policy_audit where user_id = ? and decision = 'CONFIRMED' and pickup_at > '2026-11-11 00:00:00+00' "
                + "and reasons like '%ALL_RULES_PASSED%'", SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from history_entry where action = 'AUTO_CONFIRMED' and actor_type = "
                + "'AUTOMATION' and subject_id = ?", SeedUsers.FOODSAVER.id())).isEqualTo(1);
    }

    @Test
    @Order(2)
    void gapViolationRemovesTheSignupWithBellAndMail() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(bieb), """
                {"enabled":true,"dryRun":false,"gapRuleEnabled":true,"minimumGapDays":3,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":true,"publicHolidayRuleEnabled":true,"linkedStores":[],
                 "excludedDates":["2026-11-08"]}""")).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumGapDays").value(3)).andExpect(jsonPath("$.excludedDates[0]")
                        .value("2026-11-08"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(post(S01 + "/pickups/2026-11-09T18:00:00Z/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ?",
                java.util.UUID.fromString(S01_ID), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-09T18:00:00Z")))).isZero();
        assertThat(count("select count(*) from pickup_policy_audit where user_id = ? and decision = 'DECLINED' and pickup_at = '2026-11-09 18:00:00+00' "
                + "and reasons like '%\"GAP\"%' and user_message like '%automatisch ausgeführt%'",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'PICKUP_AUTO_DECLINED'",
                SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from mail_outbox where user_id = ?", SeedUsers.FOODSAVER.id()))
                .isPositive();
    }

    @Test
    @Order(3)
    void storeAutomationPreviewRunAndAudits() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get(S01 + "/automation").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupPolicy.enabled").value(true))
                .andExpect(jsonPath("$.permissions.mayEdit").value(true));
        mvc.perform(get(S01 + "/automation").cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());
        mvc.perform(json(post(S01 + "/automation/pickup-policy/preview").cookie(bieb),
                "{\"pickupAt\":\"2026-11-08T09:00:00Z\",\"userId\":\"" + SeedUsers.FOODSAVER.id() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("SKIPPED"))
                .andExpect(jsonPath("$.reasons[0].rule").value("MANUAL_DATE"));
        mvc.perform(json(post(S01 + "/automation/pickup-policy/preview").cookie(bieb),
                "{\"pickupAt\":\"2026-12-25T18:00:00Z\",\"userId\":\"" + SeedUsers.FOODSAVER.id() + "\"}"))
                .andExpect(jsonPath("$.reasons[0].rule").value("PUBLIC_HOLIDAY"))
                .andExpect(jsonPath("$.reasons[0].text").value(Matchers.containsString("Weihnachten")));
        mvc.perform(post(S01 + "/automation/pickup-policy/run").param("dryRun", "true").cookie(bieb).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.evaluated").value(Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get(S01 + "/automation/audits").param("kind", "PICKUP").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].storeName").value("Coop Bahnhofbrücke"))
                .andExpect(jsonPath("$.page.totalItems").value(Matchers.greaterThan(10)));
    }

    @Test
    @Order(4)
    void validationCodes() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String base = """
                {"enabled":true,"dryRun":true,"gapRuleEnabled":false,"minimumGapDays":0,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":false,"publicHolidayRuleEnabled":false,"excludedDates":[],"linkedStores":[%s]}""";
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(bieb), base.formatted(
                "{\"storeIds\":[\"" + S01_ID + "\"],\"bidirectional\":false,\"windowMode\":\"SAME_DAY\","
                        + "\"windowDaysBefore\":0,\"windowDaysAfter\":0,\"graceMinutes\":20}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINKED_STORE_SELF"));
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(bieb), base.formatted(
                "{\"storeIds\":[\"00000020-0001-4000-8000-000000000002\"],\"bidirectional\":false,"
                        + "\"windowMode\":\"LAST_DAYS\",\"windowDaysBefore\":0,\"windowDaysAfter\":0,\"graceMinutes\":20}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINKED_STORE_WINDOW_INVALID"));
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(bieb), base.formatted(
                "{\"storeIds\":[\"00000020-0001-4000-8000-000000000002\"],\"bidirectional\":false,"
                        + "\"windowMode\":\"SAME_DAY\",\"windowDaysBefore\":0,\"windowDaysAfter\":0,\"graceMinutes\":20,"
                        + "\"message\":\"Hallo {{vorname}}\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TEMPLATE_VARIABLE"));
        mvc.perform(json(put(S + "07/automation/request-policy").cookie(login(SeedUsers.ORGA)),
                "{\"enabled\":true,\"dryRun\":true,\"distanceRuleEnabled\":true,\"maximumDistanceKm\":500}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void bidirectionalLinkCreatesAndDeletesTheMirror() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(json(put(S + "02/automation/pickup-policy").cookie(orga), """
                {"enabled":true,"dryRun":true,"gapRuleEnabled":false,"minimumGapDays":0,"cleaningRuleEnabled":true,
                 "experienceRuleEnabled":false,"publicHolidayRuleEnabled":false,"excludedDates":[],
                 "linkedStores":[{"storeIds":["00000020-0001-4000-8000-000000000005"],"bidirectional":true,
                 "windowMode":"LAST_DAYS","windowDaysBefore":1,"windowDaysAfter":2,"graceMinutes":45}]}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedStores[0].stores[0].name")
                        .value("Coop Stauffacher"));
        mvc.perform(get(S + "05/automation").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupPolicy.enabled").value(false))
                .andExpect(jsonPath("$.pickupPolicy.linkedStores[0].windowDaysBefore").value(2))
                .andExpect(jsonPath("$.pickupPolicy.linkedStores[0].windowDaysAfter").value(1))
                .andExpect(jsonPath("$.pickupPolicy.linkedStores[0].mirrorOf").isNotEmpty());
        mvc.perform(delete(S + "02/automation/pickup-policy").cookie(orga).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(S + "05/automation").cookie(orga))
                .andExpect(jsonPath("$.pickupPolicy.linkedStores").isEmpty());
        mvc.perform(delete(S + "02/automation/pickup-policy").cookie(orga).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    @Order(6)
    void regionOverviewExemptionsHolidaysAndAudits() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get(ZUERICH + "/automation").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.stores[?(@.store.name == 'Coop Bahnhofbrücke')].pickupPolicyEnabled")
                        .value(Matchers.contains(true)));
        mvc.perform(get(ZUERICH + "/automation/cleaning-exemptions").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.id").value(SeedUsers.BIEB.id().toString()));
        mvc.perform(json(post(ZUERICH + "/automation/cleaning-exemptions").cookie(orga),
                "{\"userId\":\"" + SeedUsers.BIEB.id() + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLEANING_EXEMPTION_EXISTS"));
        String created = mvc.perform(json(post(ZUERICH + "/automation/cleaning-exemptions").cookie(orga),
                "{\"userId\":\"" + SeedUsers.GUIDE.id() + "\",\"reason\":\"Rücken\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        mvc.perform(delete(ZUERICH + "/automation/cleaning-exemptions/" + id).cookie(orga).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(ZUERICH + "/holidays").param("year", "2026").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].name").value("Neujahr")).andExpect(jsonPath("$[0].scope").value("NATIONAL"));
        mvc.perform(get(ZUERICH + "/automation/audits").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].user.id").value(Matchers.everyItem(
                        Matchers.is(SeedUsers.FOODSAVER.id().toString()))));
        mvc.perform(get(ZUERICH + "/automation/audits").param("excludeConfirmed", "true").cookie(orga))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status").value(Matchers.everyItem(
                        Matchers.not(Matchers.in(java.util.List.of("CONFIRMED", "WOULD_CONFIRM", "APPROVED",
                                "WOULD_APPROVE"))))));
    }
}
