package network.foodsharing.poll;

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
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** V1 on the seed (clock 2026-11-02T09:00Z): listing, create rules, voting per type, results, cancel, job. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:polls-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PollFlowIT {

    static final Instant SEED_NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final String ZUERICH_STADT = "00000010-0001-4000-8000-000000000040";
    static final String KOCH = "00000011-0001-4000-8000-000000000033";
    static final String RUNNING = "00000045-0001-4000-8000-000000000001";
    static final String ENDED = "00000045-0001-4000-8000-000000000002";
    static final String UPCOMING = "00000045-0001-4000-8000-000000000003";
    static final String CANCELLED = "00000045-0001-4000-8000-000000000004";
    static final String KOCH_RUNNING = "00000045-0001-4000-8000-000000000005";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MutableClock clock;

    @AfterAll
    void resetClock() {
        clock.fixAt(SEED_NOW);
    }

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

    private String create(Cookie author, String type, String scope, String start, String end) throws Exception {
        String created = body(json(post("/api/polls"), "{\"regionId\":\"" + ZUERICH_STADT + "\",\"name\":\"" + type
                + "\",\"type\":\"" + type + "\",\"scope\":\"" + scope + "\",\"startsAt\":\"" + start + "\",\"endsAt\":\""
                + end + "\",\"options\":[\"A\",\"B\",\"C\"],\"shuffleOptions\":false,\"notifyVoters\":true}")
                .cookie(author));
        return JsonPath.read(created, "$.id");
    }

    @Test
    @Order(1)
    void seedListingStatusesAndHiddenResults() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String list = body(get("/api/regions/" + ZUERICH_STADT + "/polls").cookie(foodsaver));
        assertThat((List<String>) JsonPath.read(list, "$[*].status"))
                .containsExactlyInAnyOrder("RUNNING", "ENDED", "UPCOMING", "CANCELLED");
        mvc.perform(get("/api/polls/" + RUNNING).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mayVote").value(true)).andExpect(jsonPath("$.voted").value(false))
                .andExpect(jsonPath("$.voteCount").value(6)).andExpect(jsonPath("$.eligibleCount").doesNotExist())
                .andExpect(jsonPath("$.options[0].values").doesNotExist());
        mvc.perform(get("/api/polls/" + ENDED).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.voted").value(true)).andExpect(jsonPath("$.eligibleCount").value(9))
                .andExpect(jsonPath("$.options[0].values.length()").value(7))
                .andExpect(jsonPath("$.options[0].values[0].value").value(3))
                .andExpect(jsonPath("$.options[0].values[0].votes").value(2));
        mvc.perform(get("/api/polls/" + RUNNING).cookie(login(SeedUsers.BOT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleCount").value(9));
        mvc.perform(get("/api/polls/" + RUNNING).cookie(login(SeedUsers.NEWBIE))).andExpect(status().isForbidden());
        assertThat((List<String>) JsonPath.read(body(get("/api/polls/mine").cookie(foodsaver)), "$[*].id"))
                .containsExactly(KOCH_RUNNING, RUNNING);
    }

    @Test
    @Order(2)
    void createRulesAndScopeCounts() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String counts = body(get("/api/regions/" + ZUERICH_STADT + "/polls/scope-counts").cookie(bot));
        assertThat((List<Integer>) JsonPath.read(counts, "$[?(@.scope=='FOODSAVERS')].count")).containsExactly(9);
        assertThat((List<Integer>) JsonPath.read(counts, "$[?(@.scope=='STORE_MANAGERS')].count")).containsExactly(5);
        assertThat((List<Integer>) JsonPath.read(counts, "$[?(@.scope=='AMBASSADORS')].count")).hasSize(1);
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/polls/scope-counts").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        String base = "{\"regionId\":\"" + ZUERICH_STADT + "\",\"name\":\"X\",\"type\":\"SELECT_ONE\","
                + "\"scope\":\"FOODSAVERS\",\"shuffleOptions\":false,\"notifyVoters\":false,";
        mvc.perform(json(post("/api/polls"), base + "\"startsAt\":\"2026-11-02T09:30:00Z\",\"endsAt\":"
                + "\"2026-11-03T09:00:00Z\",\"options\":[\"A\",\"B\"]}").cookie(bot))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("POLL_DATES_INVALID"));
        mvc.perform(json(post("/api/polls"), base + "\"startsAt\":\"2026-11-02T10:00:00Z\",\"endsAt\":"
                + "\"2026-11-03T09:00:00Z\",\"options\":[\"A\",\"a\"]}").cookie(bot))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("POLL_OPTIONS_INVALID"));
        mvc.perform(json(post("/api/polls"), base + "\"startsAt\":\"2026-11-02T10:00:00Z\",\"endsAt\":"
                + "\"2026-11-03T09:00:00Z\",\"options\":[\"A\",\"B\"]}").cookie(login(SeedUsers.WELCOME)))
                .andExpect(status().isForbidden());

        String id = create(bot, "THUMBS", "FOODSAVERS", "2026-11-02T10:00:00Z", "2026-11-05T10:00:00Z");
        Cookie jumper = login(SeedUsers.JUMPER);
        assertThat(bellTypes(jumper)).contains("POLL_NEW");
        String updated = body(json(patch("/api/polls/" + id), "{\"options\":[\"Ja\",\"Nein\"],\"name\":\"Neu\"}")
                .cookie(bot));
        assertThat((List<String>) JsonPath.read(updated, "$.options[*].text")).containsExactly("Ja", "Nein");
        mvc.perform(json(patch("/api/polls/" + id), "{\"name\":\"Fremd\"}").cookie(jumper))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void votingPerTypeOnceAndOnlyWhileRunning() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String one = create(bot, "SELECT_ONE", "FOODSAVERS", "2026-11-02T10:00:00Z", "2026-11-06T10:00:00Z");
        String multi = create(bot, "SELECT_MULTIPLE", "FOODSAVERS", "2026-11-02T10:00:00Z", "2026-11-06T10:00:00Z");
        String score = create(bot, "SCORE", "STORE_MANAGERS", "2026-11-02T10:00:00Z", "2026-11-06T10:00:00Z");
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(post("/api/polls/" + one + "/vote"), "{\"values\":{\"0\":1}}").cookie(foodsaver))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLL_NOT_OPEN"));
        clock.fixAt(Instant.parse("2026-11-02T11:00:00Z"));
        mvc.perform(json(post("/api/polls/" + one + "/vote"), "{\"values\":{\"0\":1,\"1\":1}}").cookie(foodsaver))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VOTE"));
        String voted = body(json(post("/api/polls/" + one + "/vote"), "{\"values\":{\"1\":1}}").cookie(foodsaver));
        assertThat((Boolean) JsonPath.read(voted, "$.voted")).isTrue();
        assertThat((Integer) JsonPath.read(voted, "$.voteCount")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(body(get("/api/bells?size=100").cookie(foodsaver)),
                "$.items[*].link")).doesNotContain("/polls/" + one).contains("/polls/" + multi);
        mvc.perform(json(post("/api/polls/" + one + "/vote"), "{\"values\":{\"0\":1}}").cookie(foodsaver))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_VOTED"));
        body(json(post("/api/polls/" + multi + "/vote"), "{\"values\":{\"0\":1,\"2\":1}}").cookie(foodsaver));
        mvc.perform(json(post("/api/polls/" + score + "/vote"), "{\"values\":{\"0\":3,\"1\":0,\"2\":-3}}")
                .cookie(foodsaver)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ELIGIBLE"));
        Cookie bieb = login(SeedUsers.BIEB);
        mvc.perform(json(post("/api/polls/" + score + "/vote"), "{\"values\":{\"0\":3,\"1\":0}}").cookie(bieb))
                .andExpect(status().isBadRequest());
        body(json(post("/api/polls/" + score + "/vote"), "{\"values\":{\"0\":3,\"1\":0,\"2\":-3}}").cookie(bieb));

        clock.fixAt(Instant.parse("2026-11-06T10:00:00Z"));
        String result = body(get("/api/polls/" + score).cookie(bieb));
        assertThat((String) JsonPath.read(result, "$.status")).isEqualTo("ENDED");
        assertThat((List<Integer>) JsonPath.read(result, "$.options[0].values[?(@.value==3)].votes")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(result, "$.options[2].values[?(@.value==-3)].votes")).containsExactly(1);
        String multiResult = body(get("/api/polls/" + multi).cookie(bieb));
        assertThat((List<Integer>) JsonPath.read(multiResult, "$.options[*].values[0].votes")).containsExactly(1, 0, 1);
        String history = body(get("/api/history?subjectType=POLL&subjectId=" + one).cookie(login(SeedUsers.ORGA)));
        assertThat(history).contains("VOTE_CAST").doesNotContain("\"values\"");
        clock.fixAt(SEED_NOW);
    }

    @Test
    @Order(4)
    void cancelRemovesBellsAndNotifies() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String id = create(bot, "SELECT_ONE", "FOODSAVERS", "2026-11-03T10:00:00Z", "2026-11-08T10:00:00Z");
        Cookie jumper = login(SeedUsers.JUMPER);
        assertThat(bellTypes(jumper)).contains("POLL_NEW");
        mvc.perform(delete("/api/polls/" + id).with(csrf()).cookie(jumper)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/polls/" + id).with(csrf()).cookie(bot)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/polls/" + id).with(csrf()).cookie(bot)).andExpect(status().isConflict());
        String poll = body(get("/api/polls/" + id).cookie(jumper));
        assertThat((String) JsonPath.read(poll, "$.status")).isEqualTo("CANCELLED");
        assertThat((String) JsonPath.read(poll, "$.cancelledBy.id")).isEqualTo(SeedUsers.BOT.id().toString());
        List<String> bells = bellTypes(jumper);
        assertThat(bells).contains("POLL_CANCELLED");
        assertThat(JsonPath.<List<String>>read(body(get("/api/bells?size=100").cookie(jumper)),
                "$.items[?(@.link=='/polls/" + id + "')].type")).containsExactly("POLL_CANCELLED");
    }

    @Test
    @Order(5)
    void notificationJobAnnouncesStartAndEndingSoon() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        // the upcoming seed poll starts on 10 Nov, the running one ends on 9 Nov 20:00 (ran 10 days)
        clock.fixAt(Instant.parse("2026-11-09T08:00:00Z"));
        mvc.perform(post("/api/dev/jobs/poll-notifications/run").with(csrf())).andExpect(status().is2xxSuccessful());
        List<String> links = JsonPath.read(body(get("/api/bells?size=100").cookie(foodsaver)),
                "$.items[?(@.type=='POLL_ENDING_SOON')].link");
        assertThat(links).containsExactly("/polls/" + RUNNING);
        Cookie bieb = login(SeedUsers.BIEB);
        assertThat(bellTypes(bieb)).doesNotContain("POLL_ENDING_SOON"); // bieb already voted
        clock.fixAt(Instant.parse("2026-11-10T08:15:00Z"));
        mvc.perform(post("/api/dev/jobs/poll-notifications/run").with(csrf())).andExpect(status().is2xxSuccessful());
        List<String> started = JsonPath.read(body(get("/api/bells?size=100").cookie(foodsaver)),
                "$.items[?(@.type=='POLL_STARTED')].link");
        assertThat(started).contains("/polls/" + UPCOMING);
        mvc.perform(post("/api/dev/jobs/poll-notifications/run").with(csrf())).andExpect(status().is2xxSuccessful());
        List<String> again = JsonPath.read(body(get("/api/bells?size=100").cookie(foodsaver)),
                "$.items[?(@.type=='POLL_STARTED' && @.link=='/polls/" + UPCOMING + "')].id");
        assertThat(again).hasSize(1);
        clock.fixAt(SEED_NOW);
    }
}
