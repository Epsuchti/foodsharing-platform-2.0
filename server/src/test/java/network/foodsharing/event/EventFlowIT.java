package network.foodsharing.event;

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

/** E1–E3 on the seed (fixed clock 2026-11-02T09:00Z): visibility, create/update/delete, RSVP, wall, bells, ICS. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:events-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFlowIT {

    static final String ZUERICH_STADT = "00000010-0001-4000-8000-000000000040";
    static final String BRUNCH = "00000044-0002-4000-8000-000000000001";
    static final String INFO_EVENING = "00000044-0002-4000-8000-000000000002";
    static final String AUTUMN_CLEANUP = "00000044-0002-4000-8000-000000000006";
    static final String COOKING = "00000044-0002-4000-8000-000000000009";

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

    private List<String> bellTypes(Cookie user) throws Exception {
        return JsonPath.read(body(get("/api/bells?size=100").cookie(user)), "$.items[*].type");
    }

    @Test
    @Order(1)
    void membersSeeUpcomingEventsAndStrangersOnlyPublicOnes() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String list = body(get("/api/regions/" + ZUERICH_STADT + "/events").cookie(foodsaver));
        assertThat((List<String>) JsonPath.read(list, "$[*].id")).hasSize(6).contains(BRUNCH, INFO_EVENING)
                .doesNotContain(AUTUMN_CLEANUP);
        String all = body(get("/api/regions/" + ZUERICH_STADT + "/events?includePast=true").cookie(foodsaver));
        assertThat((List<String>) JsonPath.read(all, "$[*].id")).hasSize(8);

        Cookie newbie = login(SeedUsers.NEWBIE);
        String strangers = body(get("/api/regions/" + ZUERICH_STADT + "/events").cookie(newbie));
        assertThat((List<String>) JsonPath.read(strangers, "$[*].id")).containsExactly(BRUNCH);
        mvc.perform(get("/api/events/" + BRUNCH).cookie(newbie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.mayEdit").value(false))
                .andExpect(jsonPath("$.permissions.mayRsvp").value(true));
        mvc.perform(get("/api/events/" + INFO_EVENING).cookie(newbie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/events/" + COOKING).cookie(newbie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/events/00000044-0002-4000-8000-000000000099").cookie(newbie))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/events/" + BRUNCH).cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("INVITED"))
                .andExpect(jsonPath("$.location.name").value("Fairteiler Wiedikon"))
                .andExpect(jsonPath("$.wallPosts.length()").value(2))
                .andExpect(jsonPath("$.region.id").value(ZUERICH_STADT));
    }

    @Test
    @Order(2)
    void createValidatesPerTypeAndInvitesAllMembers() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        String base = "{\"regionId\":\"" + ZUERICH_STADT + "\",\"name\":\"Velotour\",\"public\":false,"
                + "\"startsAt\":\"2026-11-10T09:00:00Z\",\"endsAt\":\"2026-11-10T12:00:00Z\",";
        mvc.perform(json(post("/api/events"), base + "\"type\":\"OFFLINE\",\"inviteAllMembers\":false}").cookie(bieb))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
        mvc.perform(json(post("/api/events"), base + "\"type\":\"ONLINE\",\"inviteAllMembers\":false}").cookie(bieb))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ONLINE_LINK_REQUIRED"));
        mvc.perform(json(post("/api/events"), base.replace("2026-11-10T12", "2026-11-10T08")
                        + "\"type\":\"OTHER\",\"inviteAllMembers\":false}").cookie(bieb))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EVENT_DATES_INVALID"));
        mvc.perform(json(post("/api/events"), base + "\"type\":\"OTHER\",\"inviteAllMembers\":false}")
                .cookie(login(SeedUsers.NEWBIE))).andExpect(status().isForbidden());

        String created = body(json(post("/api/events"), base + "\"type\":\"OFFLINE\",\"inviteAllMembers\":true,"
                + "\"location\":{\"name\":\"Bürkliplatz\",\"zip\":\"8001\",\"city\":\"Zürich\"}}").cookie(bieb));
        String id = JsonPath.read(created, "$.id");
        assertThat((String) JsonPath.read(created, "$.myStatus")).isEqualTo("ACCEPTED");
        assertThat((Integer) JsonPath.read(created, "$.participants.invitedCount")).isEqualTo(11);
        assertThat((Boolean) JsonPath.read(created, "$.permissions.mayEdit")).isTrue();
        Cookie jumper = login(SeedUsers.JUMPER);
        assertThat(bellTypes(jumper)).contains("EVENT_INVITATION");
        assertThat((List<String>) JsonPath.read(body(get("/api/events/mine").cookie(jumper)), "$[*].id")).contains(id);

        // jumper accepts, bieb changes the time → jumper gets EVENT_UPDATED
        body(json(put("/api/events/" + id + "/invitation"), "{\"status\":\"ACCEPTED\"}").cookie(jumper));
        mvc.perform(json(patch("/api/events/" + id), "{\"name\":\"Velotour\"}").cookie(jumper))
                .andExpect(status().isForbidden());
        String updated = body(json(patch("/api/events/" + id), "{\"startsAt\":\"2026-11-10T10:00:00Z\"}").cookie(bieb));
        assertThat((String) JsonPath.read(updated, "$.startsAt")).startsWith("2026-11-10T10:00");
        assertThat(bellTypes(jumper)).contains("EVENT_UPDATED");
        mvc.perform(json(patch("/api/events/" + id), "{\"endsAt\":\"2026-11-10T09:00:00Z\"}").cookie(bieb))
                .andExpect(status().isBadRequest());

        // the ambassador deletes it; bells disappear
        mvc.perform(delete("/api/events/" + id).with(csrf()).cookie(login(SeedUsers.BOT)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/events/" + id).cookie(jumper)).andExpect(status().isNotFound());
        assertThat(bellTypes(jumper)).doesNotContain("EVENT_INVITATION", "EVENT_UPDATED");
        String history = body(get("/api/history?subjectType=EVENT&subjectId=" + id).cookie(login(SeedUsers.ORGA)));
        assertThat(history).contains("CREATED", "INVITATIONS_SENT", "RSVP_CHANGED", "UPDATED", "DELETED");
    }

    @Test
    @Order(3)
    void rsvpAndWall() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(json(put("/api/events/" + BRUNCH + "/invitation"), "{\"status\":\"INVITED\"}").cookie(foodsaver))
                .andExpect(status().isBadRequest());
        String answered = body(json(put("/api/events/" + BRUNCH + "/invitation"), "{\"status\":\"MAYBE\"}")
                .cookie(foodsaver));
        assertThat((String) JsonPath.read(answered, "$.myStatus")).isEqualTo("MAYBE");
        assertThat((List<String>) JsonPath.read(answered, "$.participants.maybe[*].displayName"))
                .contains("Sarah Müller");
        // past event ended more than 24 h ago
        mvc.perform(json(put("/api/events/" + AUTUMN_CLEANUP + "/invitation"), "{\"status\":\"ACCEPTED\"}")
                .cookie(foodsaver)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_RSVP_CLOSED"));

        String post = body(json(post("/api/events/" + BRUNCH + "/wall"), "{\"body\":\"Ich komme vielleicht.\"}")
                .cookie(foodsaver));
        String postId = JsonPath.read(post, "$.id");
        assertThat((Boolean) JsonPath.read(post, "$.mayDelete")).isTrue();
        assertThat(bellTypes(login(SeedUsers.BOT))).contains("EVENT_POST");
        mvc.perform(delete("/api/events/" + BRUNCH + "/wall/" + postId).with(csrf()).cookie(login(SeedUsers.JUMPER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/events/" + BRUNCH + "/wall/" + postId).with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/events/" + BRUNCH + "/wall/" + postId).with(csrf()).cookie(foodsaver))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    void icsExports() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        var one = mvc.perform(get("/api/events/" + BRUNCH + "/ics").cookie(foodsaver)).andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(one.getContentType()).startsWith("text/calendar");
        assertThat(one.getContentAsString()).contains("BEGIN:VEVENT", "SUMMARY:Foodsharing-Brunch am Fairteiler Wiedikon",
                "LOCATION:Fairteiler Wiedikon");
        String mine = mvc.perform(get("/api/events/mine/ics").cookie(foodsaver)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mine).contains("X-WR-CALNAME:foodsharing", "Foodsharing-Brunch");
        mvc.perform(get("/api/events/" + INFO_EVENING + "/ics").cookie(login(SeedUsers.NEWBIE)))
                .andExpect(status().isForbidden());
    }
}
