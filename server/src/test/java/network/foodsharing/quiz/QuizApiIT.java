package network.foodsharing.quiz;

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
import java.util.stream.Collectors;
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

/** Quiz sessions, rules and the editor end to end on the seed (e2e profile: clock fixed at SEED_NOW; plan §13.9 Q1–Q3). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:quiz-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class QuizApiIT {

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

    /** Answers every question of the running session, correctly or with the first wrong answer. */
    private String answerAll(Cookie user, String kind, boolean correctly, boolean test) throws Exception {
        String last = null;
        for (int i = 0; i < 20; i++) {
            String question = mvc.perform(get("/api/quizzes/" + kind + "/sessions/current/question")
                            .param("test", String.valueOf(test)).cookie(user))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String questionId = JsonPath.read(question, "$.question.id");
            List<String> ids = jdbc.queryForList("select cast(id as varchar) from quiz_answer where question_id = ? "
                    + "and rating = ?", String.class, java.util.UUID.fromString(questionId), correctly ? "CORRECT" : "WRONG");
            List<String> chosen = correctly ? ids : ids.subList(0, 1);
            String body = "{\"answerIds\":[" + chosen.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","))
                    + "]}";
            last = mvc.perform(json(post("/api/quizzes/" + kind + "/sessions/current/answer")
                            .param("test", String.valueOf(test)).cookie(user), body))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (Boolean.TRUE.equals(JsonPath.read(last, "$.finished"))) {
                return last;
            }
        }
        throw new AssertionError("session did not finish");
    }

    @Test
    void seedQuestionsHaveCorrectAnswersAndFiveLanguages() {
        assertThat(jdbc.queryForObject("select count(*) from quiz", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from quiz_question q where not exists (select 1 from "
                + "quiz_answer a where a.question_id = q.id and a.rating = 'CORRECT')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from quiz_question q where (select count(*) from "
                + "quiz_question_i18n t where t.owner_id = q.id) <> 5 and q.created_by is null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from quiz_answer a where (select count(*) from "
                + "quiz_answer_i18n t where t.owner_id = a.id) <> 5 and a.created_by is null", Integer.class)).isZero();
    }

    @Test
    void newbiePassesTheFoodsaverQuizAndConfirmsIt() throws Exception {
        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(get("/api/quizzes").cookie(newbie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].kind").value("FOODSAVER"))
                .andExpect(jsonPath("$[0].name").value("Foodsaver-Quiz"))
                .andExpect(jsonPath("$[0].mayTry").value(true))
                .andExpect(jsonPath("$[0].status.triesSinceLastPass").value(1))
                .andExpect(jsonPath("$[1].mayTry").value(false));
        mvc.perform(json(post("/api/quizzes/STORE_MANAGER/sessions").cookie(newbie), "{\"timed\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("QUIZ_NOT_ALLOWED"));
        mvc.perform(get("/api/quizzes/FOODSAVER/sessions/current/question").cookie(newbie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_RUNNING_SESSION"));

        mvc.perform(json(post("/api/quizzes/FOODSAVER/sessions").cookie(newbie), "{\"timed\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running.questionCount").value(10))
                .andExpect(jsonPath("$.mayStartNow").value(false));
        mvc.perform(json(post("/api/quizzes/FOODSAVER/sessions").cookie(newbie), "{\"timed\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUIZ_SESSION_RUNNING"));
        mvc.perform(get("/api/quizzes/FOODSAVER/sessions/current/question").cookie(newbie))
                .andExpect(jsonPath("$.index").value(0))
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.question.durationSeconds").doesNotExist());

        String last = answerAll(newbie, "FOODSAVER", true, false);
        assertThat((Double) JsonPath.read(last, "$.failurePoints")).isZero();
        mvc.perform(get("/api/quizzes/FOODSAVER/sessions/current/results").cookie(newbie))
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.questions.length()").value(10))
                .andExpect(jsonPath("$.requiresConfirmation").value(true))
                .andExpect(jsonPath("$.confirmationDocument").value("LEGAL_AGREEMENT"));
        assertThat(jdbc.queryForObject("select quiz_role from user_account where id = ?", String.class,
                SeedUsers.NEWBIE.id())).isEqualTo("FOODSAVER");
        mvc.perform(json(post("/api/quizzes/FOODSAVER/sessions").cookie(newbie), "{\"timed\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUIZ_ALREADY_PASSED"));

        mvc.perform(json(post("/api/quizzes/FOODSAVER/confirm").cookie(newbie), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmed").value(true));
        assertThat(jdbc.queryForObject("select role from user_account where id = ?", String.class,
                SeedUsers.NEWBIE.id())).isEqualTo("FOODSAVER");
        mvc.perform(get("/api/users/me/quiz-sessions").cookie(newbie))
                .andExpect(jsonPath("$[0].status").value("PASSED"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/dev/notifications").param("type", "QUIZ_PASSED")
                        .param("userId", SeedUsers.NEWBIE.id().toString()))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForList("select action from history_entry where subject_id = ? and secondary_subject_id "
                        + "is not null", String.class, SeedUsers.NEWBIE.id()))
                .contains("SESSION_STARTED", "QUESTION_ANSWERED", "SESSION_PASSED", "QUIZ_ROLE_RAISED",
                        "QUIZ_CONFIRMED", "ROLE_RAISED");
    }

    @Test
    void thirdFailureImposesThe30DayWait() throws Exception {
        Cookie profile = login(SeedUsers.PROFILE);
        mvc.perform(get("/api/quizzes/FOODSAVER/status").cookie(profile))
                .andExpect(jsonPath("$.triesSinceLastPass").value(2))
                .andExpect(jsonPath("$.waitDaysAfterNextFailure").value(30));
        mvc.perform(json(post("/api/quizzes/FOODSAVER/sessions").cookie(profile), "{\"timed\":false}"))
                .andExpect(status().isCreated());
        answerAll(profile, "FOODSAVER", false, false);
        mvc.perform(get("/api/quizzes/FOODSAVER/sessions/current/results").cookie(profile))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.nextTryInfo.waitDays").value(30));
        mvc.perform(json(post("/api/quizzes/FOODSAVER/sessions").cookie(profile), "{\"timed\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUIZ_WAIT_TIME"))
                .andExpect(jsonPath("$.args.days").value("30"));
        // practice sessions are allowed and have no effect
        mvc.perform(json(post("/api/quizzes/FOODSAVER/sessions").cookie(profile), "{\"timed\":false,\"test\":true}"))
                .andExpect(status().isCreated());
        answerAll(profile, "FOODSAVER", true, true);
        mvc.perform(get("/api/quizzes/FOODSAVER/status").cookie(profile))
                .andExpect(jsonPath("$.triesSinceLastPass").value(3));
        assertThat(jdbc.queryForObject("select quiz_role from user_account where id = ?", String.class,
                SeedUsers.PROFILE.id())).isNull();
    }

    @Test
    void storeManagerConfirmationNeedsTheConfidentialityNotice() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/quizzes/HYGIENE/status").cookie(foodsaver))
                .andExpect(jsonPath("$.expiresInDays").value(66));
        mvc.perform(json(post("/api/quizzes/STORE_MANAGER/sessions").cookie(foodsaver), "{\"timed\":false}"))
                .andExpect(status().isCreated());
        answerAll(foodsaver, "STORE_MANAGER", true, false);
        mvc.perform(json(post("/api/quizzes/STORE_MANAGER/confirm").cookie(foodsaver), ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.args.kind").value("CONFIDENTIALITY_NOTICE"));
        mvc.perform(json(post("/api/quizzes/AMBASSADOR/confirm").cookie(foodsaver), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUIZ_NOT_PASSED"));
    }

    @Test
    void hygieneExpiryRemindsOncePer30Days() throws Exception {
        mvc.perform(post("/api/dev/jobs/hygiene-expiry/run")).andExpect(status().isNoContent());
        mvc.perform(post("/api/dev/jobs/hygiene-expiry/run")).andExpect(status().isNoContent());
        mvc.perform(get("/api/dev/notifications").param("type", "HYGIENE_EXPIRING"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(SeedUsers.FOODSAVER.id().toString()))
                .andExpect(jsonPath("$[0].params.days").value("65"));
    }

    @Test
    void timedQuestionsTimeOutWithTheServerClock() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2026-11-02T09:00:00Z\"}")).andExpect(status().isOk());
        try {
            mvc.perform(json(post("/api/quizzes/HYGIENE/sessions").cookie(guide), "{\"timed\":true}"))
                    .andExpect(status().isCreated());
            mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2026-11-02T09:01:00Z\"}")).andExpect(status().isOk());
            mvc.perform(get("/api/quizzes/HYGIENE/sessions/current/question").cookie(guide))
                    .andExpect(jsonPath("$.questionAgeSeconds").value(60))
                    .andExpect(jsonPath("$.timedOut").value(true))
                    .andExpect(jsonPath("$.question.durationSeconds").value(45));
            mvc.perform(json(post("/api/quizzes/HYGIENE/sessions/current/answer").cookie(guide), "{\"answerIds\":[]}"))
                    .andExpect(jsonPath("$.timedOut").value(true))
                    .andExpect(jsonPath("$.solution[?(@.rating == 'CORRECT')]").isNotEmpty());
        } finally {
            mvc.perform(json(put("/api/dev/clock"), "{\"fixedAt\":\"2026-11-02T09:00:00Z\"}")).andExpect(status().isOk());
        }
    }

    @Test
    void editorsManageQuestionsOfTheirQuizzes() throws Exception {
        Cookie reports = login(SeedUsers.REPORTS);
        mvc.perform(get("/api/quizzes/REPORTS").cookie(reports))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mayEdit").value(true))
                .andExpect(jsonPath("$.names.fr").value("Quiz équipe de signalement"));
        mvc.perform(get("/api/quizzes/FOODSAVER/questions").cookie(reports)).andExpect(status().isForbidden());
        String question = mvc.perform(json(post("/api/quizzes/REPORTS/questions").cookie(reports),
                        "{\"texts\":{\"de\":\"Neue Frage?\",\"en\":\"New question?\"},\"durationSeconds\":30,"
                                + "\"mandatory\":false,\"failurePoints\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String questionId = JsonPath.read(question, "$.id");
        mvc.perform(json(post("/api/quizzes/REPORTS/questions/" + questionId + "/answers").cookie(reports),
                        "{\"texts\":{\"de\":\"Ja\"},\"explanations\":{\"de\":\"Weil.\"},\"rating\":\"CORRECT\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rating").value("CORRECT"));
        mvc.perform(json(post("/api/quizzes/REPORTS/questions/" + questionId + "/comments").cookie(reports),
                        "{\"text\":\"Bitte noch auf Französisch übersetzen\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.author.displayName").value("Céline Rochat"));
        mvc.perform(get("/api/quizzes/REPORTS/questions").cookie(reports))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.id == '" + questionId + "')].commentCount").value(1));
        mvc.perform(json(patch("/api/quizzes/REPORTS").cookie(reports), "{\"maxFailurePoints\":3}"))
                .andExpect(jsonPath("$.maxFailurePoints").value(3));
        mvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/quizzes/REPORTS/questions/" + questionId).cookie(reports), ""))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/quizzes/REPORTS/questions/" + questionId + "/comments").cookie(reports))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/quizzes/FOODSAVER/questions").cookie(orga))
                .andExpect(jsonPath("$.length()").value(12));
        mvc.perform(get("/api/users/" + SeedUsers.PROFILE.id() + "/quiz-sessions").cookie(orga))
                .andExpect(status().isOk());
        Cookie jumper = login(SeedUsers.JUMPER);
        mvc.perform(get("/api/users/" + SeedUsers.PROFILE.id() + "/quiz-sessions").cookie(jumper))
                .andExpect(status().isForbidden());
    }
}
