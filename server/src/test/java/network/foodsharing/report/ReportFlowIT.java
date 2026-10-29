package network.foodsharing.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.config.MutableClock;
import network.foodsharing.report.service.ReportService;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.AfterEach;
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
 * G1, G2, D10.3: filing (region resolution, bells, confirmation mail, rules), handling (status, consequence,
 * reminder, wall), visibility, the reminder job with a fixed clock, ORGA delete, report log and mediation.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reports-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class ReportFlowIT {

    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String STORE_S01 = "00000020-0001-4000-8000-000000000001";
    private static final String SEED_REMINDER_REPORT = "00000050-0001-4000-8000-000000000007";
    private static final Instant SEED_NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReportService reports;

    @Autowired
    private MutableClock clock;

    @AfterEach
    void releaseClockAndOptions() {
        clock.release();
        jdbc.update("delete from region_option where region_id = ? and option_key in "
                + "('REPORT_REASON_OPTIONS', 'REPORT_REASON_OTHER')", java.util.UUID.fromString(ZUERICH));
    }

    /** Sets a report option of Zürich (none are seeded; removed after each test). */
    private void zuerichOption(String key, String value) {
        jdbc.update("insert into region_option (id, version, created_at, updated_at, region_id, option_key, "
                        + "option_value) values (?, 0, ?, ?, ?, ?, ?)", java.util.UUID.randomUUID(),
                java.sql.Timestamp.from(SEED_NOW), java.sql.Timestamp.from(SEED_NOW),
                java.util.UUID.fromString(ZUERICH), key, value);
    }

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Cookie cookie,
                                                      String body) {
        return builder.with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void seedIsVisibleToTheReportTeamOnly() throws Exception {
        Cookie team = login(SeedUsers.REPORTS);
        mvc.perform(get("/api/regions/" + ZUERICH + "/reports").cookie(team)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)));
        mvc.perform(get("/api/regions/" + ZUERICH + "/reports").param("status", "IN_PROGRESS").cookie(team))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/reports/00000050-0001-4000-8000-000000000003").cookie(team))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wall.length()").value(2))
                .andExpect(jsonPath("$.permissions.mayDelete").value(false));
        mvc.perform(get("/api/regions/" + ZUERICH + "/report-log").cookie(team)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(17)));
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZUERICH + "/reports").cookie(foodsaver))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REPORTS_NOT_ACCESSIBLE"));
        mvc.perform(get("/api/reports/00000050-0001-4000-8000-000000000003").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/regions/" + ZUERICH + "/report-log").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + SeedUsers.JUMPER.id() + "/reports").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/" + SeedUsers.JUMPER.id() + "/reports").cookie(team))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/users/" + SeedUsers.JUMPER.id() + "/reports").cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void reportOptionsFollowTheReportRegionAndTheFilingRule() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String jumper = "/api/users/" + SeedUsers.JUMPER.id() + "/report-options";
        mvc.perform(get(jumper).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value(ZUERICH))
                .andExpect(jsonPath("$.reasons").value(org.hamcrest.Matchers.contains("LATE", "NO_SHOW",
                        "CANCELLATION", "SELLS")))
                .andExpect(jsonPath("$.otherAllowed").value(false))
                .andExpect(jsonPath("$.buttonEnabled").value(true))
                .andExpect(jsonPath("$.denialCode").doesNotExist());
        zuerichOption("REPORT_REASON_OPTIONS", "3");
        zuerichOption("REPORT_REASON_OTHER", "true");
        mvc.perform(get(jumper).param("storeId", STORE_S01).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value(ZUERICH))
                .andExpect(jsonPath("$.reasons.length()").value(24))
                .andExpect(jsonPath("$.otherAllowed").value(true));
        mvc.perform(get("/api/users/" + SeedUsers.FOODSAVER.id() + "/report-options").cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.buttonEnabled").value(false))
                .andExpect(jsonPath("$.denialCode").value("REPORT_SELF"));
        mvc.perform(get(jumper).cookie(login(SeedUsers.NEWBIE))).andExpect(status().isOk())
                .andExpect(jsonPath("$.buttonEnabled").value(false))
                .andExpect(jsonPath("$.denialCode").value("ACCESS_DENIED"));
        mvc.perform(get(jumper).param("storeId", java.util.UUID.randomUUID().toString()).cookie(foodsaver))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
        mvc.perform(get("/api/users/" + java.util.UUID.randomUUID() + "/report-options").cookie(foodsaver))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(get(jumper)).andExpect(status().isUnauthorized());
    }

    @Test
    void filingRejectsReasonsTheReportRegionDoesNotOffer() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String jumper = "/api/users/" + SeedUsers.JUMPER.id() + "/reports";
        String before = "select count(*) from report where reported_user_id = ?";
        int reports = count(before, SeedUsers.JUMPER.id());
        // Zürich defaults: set 1 (pickup reasons), OTHER off
        mvc.perform(json(post(jumper), foodsaver, "{\"reason\":\"OTHER\",\"reasonText\":\"Ton\","
                        + "\"message\":\"Unfreundlich bei der Übergabe.\",\"sendConfirmationMail\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REPORT_REASON_NOT_ALLOWED"));
        mvc.perform(json(post(jumper), foodsaver, "{\"reason\":\"VIOLATED_B2\",\"storeId\":\"" + STORE_S01
                        + "\",\"message\":\"Regel B2 missachtet.\",\"sendConfirmationMail\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REPORT_REASON_NOT_ALLOWED"));
        assertThat(count(before, SeedUsers.JUMPER.id())).isEqualTo(reports);

        // set 2 (rule book): the rule-book reason is accepted, a pickup reason is not
        zuerichOption("REPORT_REASON_OPTIONS", "2");
        mvc.perform(json(post(jumper), foodsaver, "{\"reason\":\"LATE\",\"message\":\"Wieder zu spät gekommen.\","
                        + "\"sendConfirmationMail\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REPORT_REASON_NOT_ALLOWED"));
        assertThat(count(before, SeedUsers.JUMPER.id())).isEqualTo(reports);
        // (filed about sleepy so the seed count of jumper's reports stays as other tests expect it)
        mvc.perform(json(post("/api/users/" + SeedUsers.SLEEPY.id() + "/reports"), foodsaver,
                        "{\"reason\":\"VIOLATED_B2\",\"storeId\":\"" + STORE_S01
                                + "\",\"message\":\"Regel B2 missachtet.\",\"sendConfirmationMail\":false}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reason").value("VIOLATED_B2"));
    }

    @Test
    void fileHandleRemindAndDelete() throws Exception {
        zuerichOption("REPORT_REASON_OTHER", "true"); // the OTHER report below
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String created = mvc.perform(json(post("/api/users/" + SeedUsers.JUMPER.id() + "/reports"), foodsaver,
                        "{\"reason\":\"LATE\",\"message\":\"Wieder eine Stunde zu spät gekommen.\",\"storeId\":\""
                                + STORE_S01 + "\",\"sendConfirmationMail\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.regionId").value(ZUERICH))
                .andExpect(jsonPath("$.store.name").value("Coop Bahnhofbrücke"))
                .andExpect(jsonPath("$.status").value("TO_DO"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        assertThat(count("select count(*) from bell where user_id = ? and type = 'REPORT_NEW' and identifier = ?",
                SeedUsers.REPORTS.id(), "REPORT_NEW-" + id)).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'REPORT_NEW'",
                SeedUsers.FOODSAVER.id())).isZero();
        // the Bell model exposes the identifier so clients can match a specific bell
        mvc.perform(get("/api/bells").param("size", "50").cookie(login(SeedUsers.REPORTS)))
                .andExpect(jsonPath("$.items[?(@.type == 'REPORT_NEW')].identifier")
                        .value(org.hamcrest.Matchers.hasItem("REPORT_NEW-" + id)));
        assertThat(count("select count(*) from mail_outbox where to_address = 'foodsaver@local' "
                + "and subject like '%Meldung%'")).isEqualTo(1);

        // rules: self, unverified, OTHER without text
        mvc.perform(json(post("/api/users/" + SeedUsers.FOODSAVER.id() + "/reports"), foodsaver,
                        "{\"reason\":\"LATE\",\"message\":\"Ich melde mich selbst.\",\"sendConfirmationMail\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_SELF"));
        mvc.perform(json(post("/api/users/" + SeedUsers.JUMPER.id() + "/reports"), foodsaver,
                        "{\"reason\":\"OTHER\",\"message\":\"Ohne Begründungstext.\",\"sendConfirmationMail\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/users/" + SeedUsers.JUMPER.id() + "/reports"), login(SeedUsers.NEWBIE),
                        "{\"reason\":\"LATE\",\"message\":\"Nicht verifiziert.\",\"sendConfirmationMail\":false}"))
                .andExpect(status().isForbidden());
        // without store: the reported member's home region
        mvc.perform(json(post("/api/users/" + SeedUsers.SLEEPY.id() + "/reports"), foodsaver,
                        "{\"reason\":\"OTHER\",\"reasonText\":\"Ton\",\"message\":\"Unfreundlich bei der Übergabe.\","
                                + "\"sendConfirmationMail\":false}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.regionId").value(ZUERICH))
                .andExpect(jsonPath("$.reasonOther").value("Ton"));

        // handling
        Cookie team = login(SeedUsers.REPORTS);
        mvc.perform(json(patch("/api/reports/" + id), team, "{\"status\":\"IN_PROGRESS\",\"consequence\":"
                        + "\"Gespräch\",\"reminderAt\":\"2026-11-01T08:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.consequence").value("Gespräch"));
        mvc.perform(json(patch("/api/reports/" + id), foodsaver, "{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/reports/" + id), team, "{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
        String post = mvc.perform(json(post("/api/reports/" + id + "/wall"), team, "{\"body\":\"Nachgefragt.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(json(post("/api/reports/" + id + "/wall"), foodsaver, "{\"body\":\"Ich auch.\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/" + id + "/wall").cookie(team)).andExpect(jsonPath("$.items.length()").value(1));
        List<String> actions = JsonPath.read(mvc.perform(get("/api/regions/" + ZUERICH + "/report-log").cookie(team)
                .param("size", "50")).andReturn().getResponse().getContentAsString(), "$.items[*].action");
        assertThat(actions).contains("CREATED", "STATUS_CHANGED", "CONSEQUENCE_SET", "REMINDER_SET",
                "WALL_POST_CREATED");

        // reminders: due ones become bells and are cleared; the seed reminder for tomorrow stays
        clock.fixAt(SEED_NOW);
        assertThat(reports.sendDueReminders()).isEqualTo(1);
        assertThat(count("select count(*) from bell where user_id = ? and type = 'REPORT_REMINDER' "
                + "and identifier = ?", SeedUsers.REPORTS.id(), "REPORT_REMINDER-" + id)).isEqualTo(1);
        assertThat(count("select count(*) from report where id = ? and reminder_at is null", id)).isEqualTo(1);
        assertThat(count("select count(*) from report where id = ? and reminder_at is not null",
                SEED_REMINDER_REPORT)).isEqualTo(1);
        clock.fixAt(Instant.parse("2026-11-03T09:00:00Z"));
        assertThat(reports.sendDueReminders()).isEqualTo(1);
        clock.release();

        // wall post delete: author; delete report: ORGA only
        String postId = JsonPath.read(post, "$.id");
        mvc.perform(delete("/api/reports/" + id + "/wall/" + postId).with(csrf()).cookie(team))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/reports/" + id).with(csrf()).cookie(team)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/reports/" + id).with(csrf()).cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/reports/" + id).cookie(team)).andExpect(status().isNotFound());
        assertThat(count("select count(*) from bell where identifier = ? and deleted_at is null", "REPORT_NEW-" + id))
                .isZero();
    }

    @Test
    void mediationTeamAndRequest() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZUERICH + "/mediation").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.team[*].id").value(org.hamcrest.Matchers.hasItem(
                        SeedUsers.REPORTS.id().toString())));
        mvc.perform(json(post("/api/regions/" + ZUERICH + "/mediation-requests"), foodsaver,
                        "{\"message\":\"Bitte helft uns, einen Streit zu klären.\",\"counterpartUserId\":\""
                                + SeedUsers.JUMPER.id() + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(count("select count(*) from bell where user_id = ? and type = 'MEDIATION_REQUEST'",
                SeedUsers.REPORTS.id())).isEqualTo(1);
        assertThat(count("select count(*) from mediation_request where requester_id = ?",
                SeedUsers.FOODSAVER.id())).isEqualTo(2);
        mvc.perform(json(post("/api/regions/" + ZUERICH + "/mediation-requests"), foodsaver, "{\"message\":\"kurz\"}"))
                .andExpect(status().isBadRequest());
    }
}
