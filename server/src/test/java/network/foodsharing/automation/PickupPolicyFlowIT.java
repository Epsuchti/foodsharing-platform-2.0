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
        // the seed keeps S-01's rules off (decision 165; M3 scenarios use its unconfirmed sign-ups); switch them on
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(login(SeedUsers.BIEB)), """
                {"enabled":true,"gapRuleEnabled":true,"minimumGapDays":2,"cleaningRuleEnabled":false,
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
    void gapViolationIsRefusedUpFrontAndShownToTheMember() throws Exception {
        // decision 165 S2: the rule blocks before the sign-up is stored; the member sees why in the list, the summary
        // and the 409; the attempt is audited as DECLINED without bell or mail
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(bieb), """
                {"enabled":true,"gapRuleEnabled":true,"minimumGapDays":3,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":true,"publicHolidayRuleEnabled":true,"linkedStores":[],
                 "excludedDates":["2026-11-08"]}""")).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumGapDays").value(3)).andExpect(jsonPath("$.excludedDates[0]")
                        .value("2026-11-08"));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String blocked = "$[?(@.pickupAt == '2026-11-09T18:00:00Z')].eligibility";
        // the confirmed pickup of Thursday 12.11. (order 1) is 2 days away
        mvc.perform(get(S01 + "/pickups").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath(blocked + ".reasons[*]").value(Matchers.hasItem("AUTOMATION_RULE")))
                .andExpect(jsonPath(blocked + ".ruleBlocks[0].rule").value(Matchers.contains("GAP")))
                .andExpect(jsonPath(blocked + ".ruleBlocks[0].params.lastPickupAt")
                        .value(Matchers.contains("2026-11-12T18:00:00Z")))
                .andExpect(jsonPath(blocked + ".ruleBlocks[0].params.gapDays").value(Matchers.contains(3)));
        mvc.perform(get(S01 + "/pickups/2026-11-09T18:00:00Z/eligibility").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.ruleBlocks[0].rule").value("GAP"));
        mvc.perform(post(S01 + "/pickups/2026-11-09T18:00:00Z/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PICKUP_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.args.reason").value("AUTOMATION_RULE"))
                .andExpect(jsonPath("$.args.rule").value("GAP"));
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ?",
                java.util.UUID.fromString(S01_ID), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-09T18:00:00Z")))).isZero();
        assertThat(count("select count(*) from pickup_policy_audit where user_id = ? and decision = 'DECLINED' and pickup_at = '2026-11-09 18:00:00+00' "
                + "and reasons like '%\"GAP\"%'", SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'PICKUP_AUTO_DECLINED'",
                SeedUsers.FOODSAVER.id())).isZero();
        // the pickup options (/pickups, home card) leave the blocked pickup out
        mvc.perform(get("/api/users/me/pickups/options").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.store.id == '" + S01_ID + "' && @.pickupAt == '2026-11-09T18:00:00Z')]")
                        .isEmpty());
        // the member summary
        mvc.perform(get(S01 + "/automation/rules").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.gapDays").value(3))
                .andExpect(jsonPath("$.experience").value(true)).andExpect(jsonPath("$.holidays").value(true))
                .andExpect(jsonPath("$.manualDates[0]").value("2026-11-08"))
                .andExpect(jsonPath("$.cleaningMonths").doesNotExist())
                .andExpect(jsonPath("$.group").doesNotExist());
        mvc.perform(get(S01 + "/automation/rules").cookie(login(SeedUsers.GUIDE))).andExpect(status().isForbidden());
        // store managers are pre-confirmed and never blocked
        mvc.perform(get(S01 + "/pickups").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eligibility.ruleBlocks[*]").value(Matchers.empty()));
    }

    @Test
    @Order(3)
    void storeAutomationAccessAndAudits() throws Exception {
        // decision 165: the store manager (bieb, no region permission) configures it; nobody else without
        // mayEditStore, also not bot, who held CAN_USE_AUTOMATIONS in zuerich-stadt before
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(get(S01 + "/automation").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupPolicy.enabled").value(true))
                .andExpect(jsonPath("$.pickupPolicy.dryRun").doesNotExist())
                .andExpect(jsonPath("$.permissions.mayEdit").value(true));
        mvc.perform(get(S01 + "/automation").cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());
        mvc.perform(get(S + "06/automation").cookie(login(SeedUsers.BOT))).andExpect(status().isForbidden());
        assertThat(count("select count(*) from region_permission where permission in ('CAN_USE_AUTOMATIONS', "
                + "'CAN_SEE_ALL_AUTOMATION_DECISIONS', 'CAN_SEE_USER_PICKUP_COUNT_GROUPING')")).isZero();
        assertThat(count("select count(*) from pickup_policy where dry_run = true")).isZero();
        mvc.perform(get(S01 + "/automation/audits").param("kind", "PICKUP").cookie(bieb)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].storeName").value("Coop Bahnhofbrücke"))
                .andExpect(jsonPath("$.page.totalItems").value(Matchers.greaterThan(10)));
    }

    @Test
    @Order(4)
    void validationCodes() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String base = """
                {"enabled":true,"gapRuleEnabled":false,"minimumGapDays":0,"cleaningRuleEnabled":false,
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
                "{\"enabled\":true,\"distanceRuleEnabled\":true,\"maximumDistanceKm\":500}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void bidirectionalLinkCreatesAndDeletesTheMirror() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(json(put(S + "02/automation/pickup-policy").cookie(orga), """
                {"enabled":true,"gapRuleEnabled":false,"minimumGapDays":0,"cleaningRuleEnabled":true,
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
    @Order(7)
    void sharedGapListDeclinesAcrossStoresOnceTheRulesAreSwitchedOn() throws Exception {
        // decisions 151/152: S-01 and S-03 share a group gap of 2 days while S-01's own gap rule is off (own rule off,
        // group on); foodsaver has a confirmed S-03 pickup on Tuesday 10.11.
        Cookie orga = login(SeedUsers.ORGA);
        String policy = """
                {"enabled":%s,"gapRuleEnabled":false,"minimumGapDays":0,"cleaningRuleEnabled":false,
                 "experienceRuleEnabled":false,"publicHolidayRuleEnabled":false,"linkedStores":[],"excludedDates":[],
                 "groupMinimumGapDays":2,"gapStoreIds":["00000020-0001-4000-8000-000000000003"]}""";
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(orga), policy.formatted("false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gapRuleEnabled").value(false))
                .andExpect(jsonPath("$.groupMinimumGapDays").value(2))
                .andExpect(jsonPath("$.gapStoreIds[0]").value("00000020-0001-4000-8000-000000000003"))
                .andExpect(jsonPath("$.gapStores[0].store.name").isNotEmpty())
                .andExpect(jsonPath("$.gapStores[0].automationActive").value(false))
                .andExpect(jsonPath("$.gapStores[0].mayEdit").value(true));
        mvc.perform(get(S + "03/automation").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupPolicy.gapStoreIds[0]").value(S01_ID))
                .andExpect(jsonPath("$.pickupPolicy.groupMinimumGapDays").value(2))
                // seed 182: S-03's own rule is off; saving the list never writes a member's own gap or switch
                .andExpect(jsonPath("$.pickupPolicy.minimumGapDays").value(0))
                .andExpect(jsonPath("$.pickupPolicy.gapRuleEnabled").value(false));
        mvc.perform(get(S01 + "/automation/gap-candidates").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.store.id == '00000020-0001-4000-8000-000000000003')].inOtherList")
                        .value(Matchers.contains(false)));
        assertThat(count("select count(*) from history_entry where action = 'GAP_LIST_CHANGED'")).isGreaterThanOrEqualTo(2);
        jdbc.update("insert into pickup_signup (id, version, created_at, updated_at, store_id, user_id, pickup_at, "
                + "confirmed) values (?, 0, ?, ?, ?, ?, ?, true)", java.util.UUID.randomUUID(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-01T10:00:00Z")),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-01T10:00:00Z")),
                java.util.UUID.fromString("00000020-0001-4000-8000-000000000003"), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-10T17:30:00Z")));
        String auditsOfThePickup = "select count(*) from pickup_policy_audit where user_id = ? "
                + "and pickup_at = '2026-11-09 18:00:00+00'";
        int auditsBefore = count(auditsOfThePickup, SeedUsers.FOODSAVER.id());
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(post(S01 + "/pickups/2026-11-09T18:00:00Z/signups").cookie(foodsaver).with(csrf()))
                .andExpect(status().isOk());
        // rules off: the sign-up stays and nothing is audited (no observe mode, decision 165)
        assertThat(count(auditsOfThePickup, SeedUsers.FOODSAVER.id())).isEqualTo(auditsBefore);
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ?",
                java.util.UUID.fromString(S01_ID), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-09T18:00:00Z")))).isEqualTo(1);

        String version = com.jayway.jsonpath.JsonPath.read(mvc.perform(get(S01 + "/automation").cookie(orga))
                .andReturn().getResponse().getContentAsString(), "$.pickupPolicy.version").toString();
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(orga), policy.formatted("true")
                .replace("\"gapStoreIds\"", "\"version\":" + version + ",\"gapStoreIds\""))).andExpect(status().isOk());
        int declinedBefore = count("select count(*) from history_entry where action = 'AUTO_DECLINED' "
                + "and subject_id = ?", SeedUsers.FOODSAVER.id());
        // switched on later: the evaluate job declines the existing sign-up
        mvc.perform(post("/api/admin/jobs/automation-evaluate/run").cookie(orga).with(csrf()))
                .andExpect(status().is2xxSuccessful());
        assertThat(count("select count(*) from pickup_signup where store_id = ? and user_id = ? and pickup_at = ?",
                java.util.UUID.fromString(S01_ID), SeedUsers.FOODSAVER.id(),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-11-09T18:00:00Z")))).isZero();
        assertThat(count("select count(*) from pickup_policy_audit where user_id = ? and decision = 'DECLINED' "
                + "and pickup_at = '2026-11-09 18:00:00+00' and reasons like '%\"GAP\"%' and reasons like "
                + "'%10.11.2026%'", SeedUsers.FOODSAVER.id())).isEqualTo(1);
        assertThat(count(auditsOfThePickup, SeedUsers.FOODSAVER.id())).isEqualTo(auditsBefore + 1);
        assertThat(count("select count(*) from history_entry where action = 'AUTO_DECLINED' and subject_id = ?",
                SeedUsers.FOODSAVER.id())).isEqualTo(declinedBefore + 1);

        // leaving: S-01 sends an empty list (decision 152), S-03 is left alone and drops out with its own gap
        mvc.perform(json(put(S01 + "/automation/pickup-policy").cookie(orga), policy.formatted("false")
                .replace("\"groupMinimumGapDays\":2,", "")
                .replace("[\"00000020-0001-4000-8000-000000000003\"]", "[]"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.gapStoreIds").isEmpty())
                .andExpect(jsonPath("$.groupMinimumGapDays").doesNotExist());
        mvc.perform(get(S + "03/automation").cookie(orga))
                .andExpect(jsonPath("$.pickupPolicy.gapStoreIds").isEmpty())
                .andExpect(jsonPath("$.pickupPolicy.minimumGapDays").value(0));
        assertThat(count("select count(*) from pickup_gap_group")).isZero();
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
