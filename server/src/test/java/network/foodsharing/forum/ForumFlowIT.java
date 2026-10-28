package network.foodsharing.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import network.foodsharing.notification.mail.CapturedMail;
import network.foodsharing.notification.mail.CapturingMailTransport;
import network.foodsharing.notification.mail.service.MailOutboxService;
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

/** F1–F5, G3, D10.3 on the seed: moderated flow, follows, mails, mentions, hide/unhide, reactions, search, log. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:forum-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ForumFlowIT {

    static final String ZUERICH_STADT = "00000010-0001-4000-8000-000000000040";
    static final String WELCOME_THREAD = "00000043-0001-4000-8000-000000000001";
    static final String FRIDGE_THREAD = "00000043-0001-4000-8000-000000000003";
    static final String CLOSED_THREAD = "00000043-0001-4000-8000-000000000006";
    static final String DISPUTE_THREAD = "00000043-0001-4000-8000-000000000009";
    static final String INACTIVE_THREAD = "00000043-0001-4000-8000-000000000014";
    static final String WELCOME_POST_BOT = "00000043-0002-4000-8000-000000000001";
    static final String WELCOME_POST_FOODSAVER = "00000043-0002-4000-8000-000000000002";
    static final String HIDDEN_POST = "00000043-0002-4000-8000-000000000030";
    static final String JUMPER_POST = "00000043-0002-4000-8000-000000000032";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MailOutboxService outbox;
    @Autowired
    private CapturingMailTransport captured;

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

    private List<String> bellTypes(Cookie user) throws Exception {
        return JsonPath.read(body(get("/api/bells?size=100").cookie(user)), "$.items[*].type");
    }

    @Test
    @Order(1)
    void seedListingHidesInactiveThreadsAndHiddenBodies() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String list = body(get("/api/regions/" + ZUERICH_STADT + "/forum/threads?size=50").cookie(foodsaver));
        assertThat((Integer) JsonPath.read(list, "$.page.totalItems")).isEqualTo(13);
        assertThat((Boolean) JsonPath.read(list, "$.items[0].sticky")).isTrue();
        assertThat((Boolean) JsonPath.read(list, "$.items[1].sticky")).isTrue();
        assertThat((List<String>) JsonPath.read(list, "$.items[*].id")).doesNotContain(INACTIVE_THREAD);
        mvc.perform(get("/api/forum/threads/" + INACTIVE_THREAD).cookie(foodsaver)).andExpect(status().isNotFound());
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/threads?ambassadorForum=true").cookie(foodsaver))
                .andExpect(status().isForbidden());

        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/threads?size=50").cookie(bot))
                .andExpect(jsonPath("$.page.totalItems").value(14));
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/threads?ambassadorForum=true").cookie(bot))
                .andExpect(jsonPath("$.page.totalItems").value(3));

        String thread = body(get("/api/forum/threads/" + DISPUTE_THREAD).cookie(foodsaver));
        assertThat((String) JsonPath.read(thread, "$.posts[1].hidden.reason")).isEqualTo("Beleidigung");
        assertThat((List<Object>) JsonPath.read(thread, "$.posts[?(@.id == '" + HIDDEN_POST + "')].body")).containsOnlyNulls();
        assertThat((Boolean) JsonPath.read(thread, "$.permissions.mayModerate")).isFalse();
        mvc.perform(get("/api/forum/threads/" + DISPUTE_THREAD).cookie(bot))
                .andExpect(jsonPath("$.posts[1].body").exists())
                .andExpect(jsonPath("$.permissions.mayModerate").value(true))
                .andExpect(jsonPath("$.permissions.mayHide").value(true));
        mvc.perform(get("/api/forum/threads/" + WELCOME_THREAD).cookie(foodsaver))
                .andExpect(jsonPath("$.following.bell").value(false));
    }

    @Test
    @Order(2)
    void moderatedThreadIsActivatedAndNotifiesFollowersAndMailFollowers() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        String created = body(json(post("/api/regions/" + ZUERICH_STADT + "/forum/threads"),
                "{\"title\":\"Suche Mitfahrgelegenheit\",\"body\":\"Wer fährt am Samstag nach Oerlikon?\","
                        + "\"followBell\":true}").cookie(jumper));
        String threadId = JsonPath.read(created, "$.id");
        assertThat((Boolean) JsonPath.read(created, "$.active")).isFalse();
        assertThat((Boolean) JsonPath.read(created, "$.following.bell")).isTrue();

        Cookie bot = login(SeedUsers.BOT);
        assertThat(bellTypes(bot)).contains("FORUM_THREAD_TO_ACTIVATE");
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/forum/threads/" + threadId).cookie(foodsaver)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/forum/threads/" + threadId + "/posts"), "{\"body\":\"x\"}").cookie(jumper))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THREAD_NOT_ACTIVE"));

        mvc.perform(json(patch("/api/forum/threads/" + threadId), "{\"active\":true}").cookie(foodsaver))
                .andExpect(status().isNotFound());
        mvc.perform(json(patch("/api/forum/threads/" + threadId), "{\"active\":true}").cookie(bot))
                .andExpect(jsonPath("$.active").value(true));
        assertThat(bellTypes(bot)).doesNotContain("FORUM_THREAD_TO_ACTIVATE");
        assertThat(bellTypes(foodsaver)).contains("FORUM_NEW_THREAD");

        mvc.perform(json(put("/api/forum/threads/" + threadId + "/follow"), "{\"bell\":false,\"mail\":true}")
                .cookie(foodsaver)).andExpect(jsonPath("$.mail").value(true));
        captured.clear();
        mvc.perform(json(post("/api/forum/threads/" + threadId + "/posts"),
                "{\"body\":\"Ich fahre um 10 Uhr, @[Nina Schneider](user:" + SeedUsers.BIEB.id() + ") kommt mit.\"}")
                .cookie(jumper)).andExpect(status().isCreated()).andExpect(jsonPath("$.mayEdit").value(true));
        outbox.processDue();
        List<CapturedMail> mails = captured.list("foodsaver@local");
        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().subject()).contains("Suche Mitfahrgelegenheit");
        assertThat(bellTypes(login(SeedUsers.BIEB))).contains("FORUM_MENTION");
        mvc.perform(get("/api/forum/threads/" + threadId).cookie(foodsaver)).andExpect(jsonPath("$.postCount").value(2));

        String log = body(get("/api/regions/" + ZUERICH_STADT + "/moderation-log?size=50").cookie(bot));
        assertThat((List<String>) JsonPath.read(log, "$.items[*].action")).contains("THREAD_ACTIVATED", "POST_HIDDEN");
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/moderation-log").cookie(foodsaver))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void hideUnhideReactionsEditAndPermissions() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        Cookie jumper = login(SeedUsers.JUMPER);
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(post("/api/forum/posts/" + JUMPER_POST + "/hide"), "{\"reason\":\"Off-topic\"}").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/forum/posts/" + JUMPER_POST + "/hide"), "{\"reason\":\"Off-topic\"}").cookie(bot))
                .andExpect(jsonPath("$.hidden.reason").value("Off-topic"));
        mvc.perform(json(post("/api/forum/posts/" + JUMPER_POST + "/hide"), "{\"reason\":\"again\"}").cookie(bot))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_ALREADY_HIDDEN"));
        assertThat(bellTypes(jumper)).contains("POST_HIDDEN");
        mvc.perform(delete("/api/forum/posts/" + JUMPER_POST + "/hide").with(csrf()).cookie(bot))
                .andExpect(jsonPath("$.hidden").doesNotExist());

        mvc.perform(put("/api/forum/posts/" + WELCOME_POST_BOT + "/reactions/thumbsup").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/forum/posts/" + WELCOME_POST_BOT + "/reactions/100").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        String thread = body(get("/api/forum/threads/" + WELCOME_THREAD).cookie(foodsaver));
        assertThat((List<String>) JsonPath.read(thread, "$.posts[0].reactions[*].key")).contains("thumbsup", "100");
        mvc.perform(delete("/api/forum/posts/" + WELCOME_POST_BOT + "/reactions/100").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());

        mvc.perform(json(patch("/api/forum/posts/" + WELCOME_POST_FOODSAVER), "{\"body\":\"Danke! Wo sind die Fairteiler, @[Julien Favre](user:" + SeedUsers.GUIDE.id() + ")?\"}")
                .cookie(foodsaver)).andExpect(jsonPath("$.editedAt").exists());
        assertThat(bellTypes(login(SeedUsers.GUIDE))).contains("FORUM_MENTION");
        mvc.perform(json(patch("/api/forum/posts/" + WELCOME_POST_BOT), "{\"body\":\"gehackt\"}").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/forum/threads/" + CLOSED_THREAD + "/posts"), "{\"body\":\"Noch Platz?\"}")
                .cookie(foodsaver)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("THREAD_CLOSED"));
        mvc.perform(json(post("/api/forum/threads/" + CLOSED_THREAD + "/posts"), "{\"body\":\"Moderatorhinweis\"}")
                .cookie(bot)).andExpect(status().isCreated());
        mvc.perform(delete("/api/forum/threads/" + FRIDGE_THREAD).with(csrf()).cookie(bot)).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/forum/threads/" + FRIDGE_THREAD), "{\"sticky\":true}").cookie(foodsaver))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/forum/threads/" + FRIDGE_THREAD), "{\"sticky\":true,\"status\":\"CLOSED\"}")
                .cookie(bot)).andExpect(jsonPath("$.sticky").value(true)).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(delete("/api/forum/threads/" + INACTIVE_THREAD).with(csrf()).cookie(bot))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(4)
    void searchAndForumFollow() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/search?q=kühlschrank").cookie(foodsaver))
                .andExpect(jsonPath("$.items[0].thread.id").value(FRIDGE_THREAD));
        String hits = body(get("/api/regions/" + ZUERICH_STADT + "/forum/search?q=techniker&searchBody=true")
                .cookie(foodsaver));
        assertThat((String) JsonPath.read(hits, "$.items[0].snippet")).containsIgnoringCase("techniker");
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/search?q=idioten&searchBody=true").cookie(foodsaver))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/forum/search?q=brot").cookie(foodsaver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/forum/search?q=brot").cookie(login(SeedUsers.ORGA)))
                .andExpect(jsonPath("$.page.totalItems").value(3));

        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/follow").cookie(foodsaver))
                .andExpect(jsonPath("$.following").value(true));
        mvc.perform(json(put("/api/regions/" + ZUERICH_STADT + "/forum/follow"), "{\"following\":false}").cookie(foodsaver))
                .andExpect(jsonPath("$.following").value(false));
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/forum/follow").cookie(foodsaver))
                .andExpect(jsonPath("$.following").value(false));
    }
}
