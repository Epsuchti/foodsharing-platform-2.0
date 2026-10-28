package network.foodsharing.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** SV1 on the seed: manager access, co-authors, the store link, once-only submission, results. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:surveys-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SurveyFlowIT {

    static final String SATISFACTION = "00000046-0001-4000-8000-000000000001";
    static final String MENU = "00000046-0001-4000-8000-000000000002";
    static final String S01 = "00000020-0001-4000-8000-000000000001";
    static final String Q_STORE = "00000046-0003-4000-8000-000000000001";
    static final String Q_SINGLE = "00000046-0003-4000-8000-000000000002";
    static final String Q_BOOL = "00000046-0003-4000-8000-000000000004";

    @Autowired
    private MockMvc mvc;

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
        return mvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @Order(1)
    void managersSeeTheirSurveysOthersDoNot() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String list = body(get("/api/surveys").cookie(bot));
        assertThat((List<String>) JsonPath.read(list, "$[*].id")).containsExactly(SATISFACTION);
        assertThat((Integer) JsonPath.read(list, "$[0].responseCount")).isEqualTo(6);
        Cookie orga = login(SeedUsers.ORGA);
        assertThat((List<String>) JsonPath.read(body(get("/api/surveys").cookie(orga)), "$[*].id"))
                .containsExactlyInAnyOrder(SATISFACTION, MENU);
        mvc.perform(get("/api/surveys/" + MENU).cookie(bot)).andExpect(status().isNotFound());
        mvc.perform(get("/api/surveys").cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/surveys/" + SATISFACTION).cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mayDelete").value(false))
                .andExpect(jsonPath("$.questions.length()").value(5));
        String results = body(get("/api/surveys/" + SATISFACTION + "/results").cookie(orga));
        assertThat((Integer) JsonPath.read(results, "$.responseCount")).isEqualTo(6);
        assertThat((String) JsonPath.read(results, "$.rows[0].store.name")).isEqualTo("Coop Bahnhofbrücke");
    }

    @Test
    @Order(2)
    void fillOnceThroughTheStoreLink() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/surveys/" + SATISFACTION + "/form?storeId=" + S01).cookie(foodsaver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alreadySubmitted").value(false))
                .andExpect(jsonPath("$.store.id").value(S01));
        String answers = "\"answers\":[{\"questionId\":\"" + Q_SINGLE + "\",\"values\":[\"Zufrieden\"]},"
                + "{\"questionId\":\"" + Q_BOOL + "\",\"values\":[\"true\"]}]}";
        mvc.perform(json(post("/api/surveys/" + SATISFACTION + "/responses"), "{" + answers).cookie(foodsaver))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_ANSWER_LINK_ONLY"));
        mvc.perform(json(post("/api/surveys/" + SATISFACTION + "/responses"), "{\"storeId\":\"" + S01
                        + "\",\"answers\":[{\"questionId\":\"" + Q_SINGLE + "\",\"values\":[\"Vielleicht\"]}]}")
                .cookie(foodsaver)).andExpect(status().isBadRequest());
        body(json(post("/api/surveys/" + SATISFACTION + "/responses"), "{\"storeId\":\"" + S01 + "\"," + answers)
                .cookie(foodsaver));
        mvc.perform(json(post("/api/surveys/" + SATISFACTION + "/responses"), "{\"storeId\":\"" + S01 + "\","
                + answers).cookie(foodsaver)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_ALREADY_SUBMITTED"));
        String results = body(get("/api/surveys/" + SATISFACTION + "/results").cookie(login(SeedUsers.BOT)));
        assertThat((Integer) JsonPath.read(results, "$.responseCount")).isEqualTo(7);
        assertThat((String) JsonPath.read(results, "$.rows[6].answers['" + Q_STORE + "'][0]"))
                .isEqualTo(S01);
    }

    @Test
    @Order(3)
    void createShareEditAndDelete() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String candidates = body(get("/api/surveys/coauthor-candidates?q=Dan").cookie(bot));
        assertThat((List<String>) JsonPath.read(candidates, "$[*].id")).contains(SeedUsers.ORGA.id().toString());
        String write = "{\"title\":\"Neue Umfrage\",\"questions\":[{\"type\":\"TEXT\",\"label\":\"Frage\","
                + "\"required\":true,\"options\":[]}],\"coauthorIds\":[\"" + SeedUsers.ORGA.id() + "\"]}";
        mvc.perform(json(post("/api/surveys"), write.replace(SeedUsers.ORGA.id().toString(),
                SeedUsers.FOODSAVER.id().toString())).cookie(bot)).andExpect(status().isBadRequest());
        String created = body(json(post("/api/surveys"), write).cookie(bot));
        String id = JsonPath.read(created, "$.id");
        String questionId = JsonPath.read(created, "$.questions[0].id");
        Cookie orga = login(SeedUsers.ORGA);
        assertThat((List<String>) JsonPath.read(body(get("/api/bells?size=100").cookie(orga)), "$.items[*].type"))
                .contains("SURVEY_SHARED");
        String updated = body(json(put("/api/surveys/" + id), "{\"title\":\"Umbenannt\",\"questions\":[{\"id\":\""
                + questionId + "\",\"type\":\"TEXT\",\"label\":\"Frage 1\",\"required\":false,\"options\":[]},"
                + "{\"type\":\"BOOLEAN\",\"label\":\"Ja?\",\"required\":false,\"options\":[]}]}").cookie(orga));
        assertThat((String) JsonPath.read(updated, "$.questions[0].id")).isEqualTo(questionId);
        assertThat((List<String>) JsonPath.read(updated, "$.coauthors[*].id")).containsExactly(SeedUsers.ORGA.id().toString());
        mvc.perform(delete("/api/surveys/" + id).with(csrf()).cookie(orga)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/surveys/" + id).with(csrf()).cookie(bot)).andExpect(status().isNoContent());
        mvc.perform(get("/api/surveys/" + id).cookie(bot)).andExpect(status().isNotFound());
    }
}
