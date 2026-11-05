package network.foodsharing.intropickup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Q4 (decision 168) end to end on the seed (e2e profile, clock fixed at SEED_NOW): the EinAb guide quiz is listed only
 * from the region's pickup threshold, a pass rings the ambassadors, only region administrators release people with
 * a pass, the generic grant refuses the guide permission, and the deadline job reminds and then revokes.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guide-quiz-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GuideQuizApiIT {

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

    private List<String> bellsTo(String type) throws Exception {
        String body = mvc.perform(get("/api/dev/notifications").param("type", type).param("channel", "BELL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].userId");
    }

    private void passGuideQuiz(Cookie user) throws Exception {
        mvc.perform(json(post("/api/quizzes/EINAB_GUIDE/sessions").cookie(user), "{\"timed\":false}"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 20; i++) {
            String question = mvc.perform(get("/api/quizzes/EINAB_GUIDE/sessions/current/question").cookie(user))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String questionId = JsonPath.read(question, "$.question.id");
            List<String> ids = jdbc.queryForList("select cast(id as varchar) from quiz_answer where question_id = ? "
                    + "and rating = 'CORRECT'", String.class, java.util.UUID.fromString(questionId));
            String body = "{\"answerIds\":[" + ids.stream().map(id -> "\"" + id + "\"")
                    .collect(Collectors.joining(",")) + "]}";
            String result = mvc.perform(json(post("/api/quizzes/EINAB_GUIDE/sessions/current/answer").cookie(user),
                    body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (Boolean.TRUE.equals(JsonPath.read(result, "$.finished"))) {
                return;
            }
        }
        throw new AssertionError("session did not finish");
    }

    @Test
    @Order(1)
    void theQuizRowAndTheWelcomeGrantMigration() {
        assertThat(jdbc.queryForObject("select count(*) from quiz where kind = 'EINAB_GUIDE' and id = "
                + "'00000013-0001-4000-8000-000000000008'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select guide_quiz_min_pickups from region where id = ?", Integer.class,
                java.util.UUID.fromString(ZURICH))).isEqualTo(25);
    }

    @Test
    @Order(2)
    void beforeTheDeadlineTheJobRemindsGuidesWithoutAPassOnce() throws Exception {
        mvc.perform(post("/api/dev/jobs/guide-quiz-deadline/run")).andExpect(status().isNoContent());
        mvc.perform(post("/api/dev/jobs/guide-quiz-deadline/run")).andExpect(status().isNoContent());
        List<String> reminded = bellsTo("GUIDE_QUIZ_REQUIRED");
        // bot and welcome hold direct grants without a pass; guide@local passed in the seed
        assertThat(reminded).containsExactlyInAnyOrder(SeedUsers.BOT.id().toString(),
                SeedUsers.WELCOME.id().toString());
    }

    @Test
    @Order(3)
    void theQuizUnlocksAtTheRegionThresholdAndAPassRingsTheAmbassadors() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        // 24 confirmed past pickups, threshold 25: not listed, not startable
        mvc.perform(get("/api/quizzes").cookie(foodsaver))
                .andExpect(jsonPath("$[?(@.kind == 'EINAB_GUIDE')]").isEmpty());
        mvc.perform(json(post("/api/quizzes/EINAB_GUIDE/sessions").cookie(foodsaver), "{\"timed\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("QUIZ_NOT_ALLOWED"));
        mvc.perform(get("/api/onboarding/journey").cookie(foodsaver))
                .andExpect(jsonPath("$.tracks[?(@.key == 'GUIDE')].steps[0].status").value("LOCKED"))
                .andExpect(jsonPath("$.tracks[?(@.key == 'GUIDE')].steps[0].blockedArgs.done").value("24"))
                .andExpect(jsonPath("$.tracks[?(@.key == 'GUIDE')].steps[0].blockedArgs.required").value("25"));

        // the ambassador lowers the threshold of Zürich
        Cookie bot = login(SeedUsers.BOT);
        String settings = mvc.perform(get("/api/regions/" + ZURICH + "/settings").cookie(bot))
                .andExpect(jsonPath("$.introPickups.guideQuizMinPickups").value(25))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> intro = JsonPath.read(settings, "$.introPickups");
        intro.put("guideQuizMinPickups", 24);
        mvc.perform(json(patch("/api/regions/" + ZURICH + "/settings").cookie(bot),
                        new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("introPickups", intro))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.introPickups.guideQuizMinPickups").value(24));

        mvc.perform(get("/api/quizzes").cookie(foodsaver))
                .andExpect(jsonPath("$[?(@.kind == 'EINAB_GUIDE')].mayTry").value(true));
        passGuideQuiz(foodsaver);
        mvc.perform(get("/api/quizzes/EINAB_GUIDE/status").cookie(foodsaver))
                .andExpect(jsonPath("$.lastSessionStatus").value("PASSED"))
                .andExpect(jsonPath("$.requiresConfirmation").value(false));
        assertThat(bellsTo("GUIDE_QUIZ_PASSED")).contains(SeedUsers.BOT.id().toString())
                .doesNotContain(SeedUsers.FOODSAVER.id().toString());
        mvc.perform(get("/api/onboarding/journey").cookie(foodsaver))
                .andExpect(jsonPath("$.tracks[?(@.key == 'GUIDE')].steps[0].status").value("WAITING_FOR_OTHERS"));
    }

    @Test
    @Order(4)
    void onlyRegionAdministratorsReleaseAndOnlyPeopleWithAPass() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guide-candidates").cookie(bot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.JUMPER.id() + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.GUIDE.id() + "')]").isEmpty());
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guides").cookie(bot))
                .andExpect(jsonPath("$.guideQuizDeadline").value("2027-01-15"))
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.GUIDE.id() + "')].guideQuizPassedAt")
                        .isNotEmpty());

        // the generic grant refuses the guide permission
        mvc.perform(json(put("/api/regions/" + ZURICH + "/permissions/" + SeedUsers.JUMPER.id()
                        + "/CAN_GIVE_INTRO_PICKUPS").cookie(bot), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_GUIDE_RELEASE"));

        // a guide admin (CAN_MANAGE_USERS, not an ambassador) may not release, but may disable
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.GUIDE.id() + "/admin")
                .cookie(bot), "{\"admin\":true}")).andExpect(status().isOk());
        Cookie guide = login(SeedUsers.GUIDE);
        String jumperEnabled = "/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.JUMPER.id() + "/enabled";
        mvc.perform(json(put(jumperEnabled).cookie(guide), "{\"enabled\":true}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guide-candidates").cookie(guide)).andExpect(status().isOk());

        // no pass: 409; with a pass: released
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.SLEEPY.id() + "/enabled")
                        .cookie(bot), "{\"enabled\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUIDE_QUIZ_NOT_PASSED"));
        mvc.perform(json(put(jumperEnabled).cookie(bot), "{\"enabled\":true,\"reason\":\"Quiz bestanden\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/regions/" + ZURICH + "/admin/guide-candidates").cookie(bot))
                .andExpect(jsonPath("$.items[?(@.user.id == '" + SeedUsers.JUMPER.id() + "')]").isEmpty());
        mvc.perform(json(put(jumperEnabled).cookie(guide), "{\"enabled\":false,\"reason\":\"Pause\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(json(put("/api/regions/" + ZURICH + "/admin/guides/" + SeedUsers.GUIDE.id() + "/admin")
                .cookie(bot), "{\"admin\":false}")).andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void fromTheDeadlineTheJobRevokesGuidesWithoutAPassOnce() throws Exception {
        mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2027-01-15T07:15:00Z\"}")).andExpect(status().isOk());
        try {
            mvc.perform(post("/api/dev/jobs/guide-quiz-deadline/run")).andExpect(status().isNoContent());
            mvc.perform(post("/api/dev/jobs/guide-quiz-deadline/run")).andExpect(status().isNoContent());
        } finally {
            mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2026-11-02T09:00:00Z\"}")).andExpect(status().isOk());
        }
        assertThat(bellsTo("GUIDE_RELEASE_REVOKED")).containsExactlyInAnyOrder(SeedUsers.BOT.id().toString(),
                SeedUsers.WELCOME.id().toString());
        assertThat(jdbc.queryForList("select cast(user_id as varchar) from region_permission where permission = "
                + "'CAN_GIVE_INTRO_PICKUPS'", String.class)).containsExactly(SeedUsers.GUIDE.id().toString());
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'GUIDE_DISABLED' and "
                + "reason = 'guide quiz not passed'", Integer.class)).isEqualTo(3);
    }
}
