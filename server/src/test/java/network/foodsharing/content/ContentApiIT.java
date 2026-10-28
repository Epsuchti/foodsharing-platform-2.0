package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Content, learning chapters, legal documents and consents end to end on the seed (e2e profile). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:content-api-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class ContentApiIT {

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
    void publicContentAndLearningChapters() throws Exception {
        mvc.perform(get("/api/public/content/learning").param("lang", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].key").value("rules"))
                .andExpect(jsonPath("$[0].title").value("Les règles"))
                .andExpect(jsonPath("$[2].estimatedMinutes").value(3));
        mvc.perform(get("/api/public/content/learning/rules").param("lang", "gws"))
                .andExpect(jsonPath("$.title").value("D Regle"))
                .andExpect(jsonPath("$.nextChapterKey").value("hygiene"))
                .andExpect(jsonPath("$.fallback").value(false));
        mvc.perform(get("/api/public/content/faq"))
                .andExpect(jsonPath("$.language").value("de")).andExpect(jsonPath("$.kind").value("PAGE"));
        mvc.perform(get("/api/public/content/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));
        mvc.perform(get("/api/public/legal/LEGAL_AGREEMENT").param("lang", "it"))
                .andExpect(jsonPath("$.version").value("2026-01"))
                .andExpect(jsonPath("$.language").value("it"));
    }

    @Test
    void consentsAreInTheSessionAndAccepted() throws Exception {
        Cookie newbie = login(SeedUsers.NEWBIE);
        mvc.perform(get("/api/auth/session").cookie(newbie))
                .andExpect(jsonPath("$.pendingConsents").value(org.hamcrest.Matchers.contains("PRIVACY_POLICY")));
        mvc.perform(json(post("/api/legal/consents").cookie(newbie), "{\"kind\":\"PRIVACY_POLICY\",\"version\":\"2025-01\"}"))
                .andExpect(status().isNotFound());
        String current = JsonPath.read(mvc.perform(get("/api/public/legal/PRIVACY_POLICY")).andReturn().getResponse()
                .getContentAsString(), "$.version");
        mvc.perform(json(post("/api/legal/consents").cookie(newbie),
                        "{\"kind\":\"PRIVACY_POLICY\",\"version\":\"" + current + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(false));
        mvc.perform(get("/api/auth/session").cookie(newbie)).andExpect(jsonPath("$.pendingConsents").isEmpty());
        Cookie quiz = login(SeedUsers.QUIZ);
        mvc.perform(get("/api/legal/consents").cookie(quiz))
                .andExpect(jsonPath("$[?(@.kind == 'LEGAL_AGREEMENT')].pending").value(true));
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'CONSENT_GIVEN' "
                + "and subject_id = ?", Integer.class, SeedUsers.NEWBIE.id())).isEqualTo(1);
    }

    @Test
    void orgaPublishesAVersionThatRequiresReconsent() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(post("/api/legal/PRIVACY_POLICY/versions").cookie(foodsaver),
                        "{\"version\":\"2026-11\",\"summaries\":{},\"bodies\":{\"de\":\"Neu\"},\"requireReconsent\":true}"))
                .andExpect(status().isForbidden());
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(json(post("/api/legal/PRIVACY_POLICY/versions").cookie(orga),
                        "{\"version\":\"2026-11\",\"summaries\":{\"de\":\"Neu\"},\"bodies\":{\"de\":\"Neu\"},"
                                + "\"requireReconsent\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/auth/session").cookie(foodsaver))
                .andExpect(jsonPath("$.pendingConsents").value(org.hamcrest.Matchers.contains("PRIVACY_POLICY")));
        mvc.perform(json(post("/api/legal/consents").cookie(foodsaver), "{\"kind\":\"PRIVACY_POLICY\",\"version\":\"2026-01\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEGAL_VERSION_OUTDATED"));
        mvc.perform(get("/api/legal/PRIVACY_POLICY/versions").cookie(orga))
                .andExpect(jsonPath("$[0].version").value("2026-11"));
    }

    @Test
    void storeManagerConfirmationWorksWithTheConfidentialityNotice() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        mvc.perform(json(post("/api/quizzes/STORE_MANAGER/sessions").cookie(jumper), "{\"timed\":false}"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 10; i++) {
            String question = mvc.perform(get("/api/quizzes/STORE_MANAGER/sessions/current/question").cookie(jumper))
                    .andReturn().getResponse().getContentAsString();
            String questionId = JsonPath.read(question, "$.question.id");
            List<String> ids = jdbc.queryForList("select cast(id as varchar) from quiz_answer where question_id = ? "
                    + "and rating = 'CORRECT'", String.class, java.util.UUID.fromString(questionId));
            String result = mvc.perform(json(post("/api/quizzes/STORE_MANAGER/sessions/current/answer").cookie(jumper),
                    "{\"answerIds\":[" + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","))
                            + "]}")).andReturn().getResponse().getContentAsString();
            if (Boolean.TRUE.equals(JsonPath.read(result, "$.finished"))) {
                break;
            }
        }
        mvc.perform(json(post("/api/quizzes/STORE_MANAGER/confirm").cookie(jumper), ""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(json(post("/api/legal/consents").cookie(jumper),
                        "{\"kind\":\"CONFIDENTIALITY_NOTICE\",\"version\":\"2026-01\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/quizzes/STORE_MANAGER/confirm").cookie(jumper), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmed").value(true));
        assertThat(jdbc.queryForObject("select role from user_account where id = ?", String.class,
                SeedUsers.JUMPER.id())).isEqualTo("STORE_MANAGER");
    }

    @Test
    void editorsUpsertPages() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/content/pages").cookie(bot)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(32)); // B11 seed: 38 pages, 6 with a non-PR editor function
        mvc.perform(json(put("/api/content/pages/press").cookie(bot), "{\"titles\":{\"de\":\"Presse\"},"
                        + "\"bodies\":{\"de\":\"Kontakt\"},\"kind\":\"PAGE\",\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.languages[0]").value("de"));
        mvc.perform(get("/api/public/content/press")).andExpect(jsonPath("$.title").value("Presse"));
        Cookie jumper = login(SeedUsers.JUMPER);
        mvc.perform(get("/api/content/pages").cookie(jumper)).andExpect(status().isForbidden());
        mvc.perform(get("/api/content/pages/press").cookie(bot))
                .andExpect(jsonPath("$.bodies.de").value("Kontakt"));
    }
}
