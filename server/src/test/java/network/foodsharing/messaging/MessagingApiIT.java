package network.foodsharing.messaging;

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
import java.util.UUID;
import network.foodsharing.store.service.TeamChatPort;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** N1/S10/AU4 on the seed: direct chats, errors, read state, store team/jumper chats following the team, adverts. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:messaging-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class MessagingApiIT {

    static final String S01 = "00000020-0001-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TeamChatPort teamChat;

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
    void directConversationSendReadAndErrors() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        Cookie welcome = login(SeedUsers.WELCOME);
        String conversation = body(json(post("/api/conversations/lookup"), "{\"memberIds\":[\"" + SeedUsers.WELCOME.id()
                + "\"]}").cookie(guide));
        String id = JsonPath.read(conversation, "$.id");
        assertThat((String) JsonPath.read(conversation, "$.kind")).isEqualTo("DIRECT");
        assertThat((String) JsonPath.read(body(json(post("/api/conversations/lookup"), "{\"memberIds\":[\""
                + SeedUsers.GUIDE.id() + "\"]}").cookie(welcome)), "$.id")).isEqualTo(id);

        String first = body(json(post("/api/conversations/" + id + "/messages"), "{\"body\":\"Hallo\",\"clientKey\":\"a1\"}")
                .cookie(guide));
        String again = body(json(post("/api/conversations/" + id + "/messages"), "{\"body\":\"Hallo\",\"clientKey\":\"a1\"}")
                .cookie(guide));
        assertThat((String) JsonPath.read(again, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        mvc.perform(json(post("/api/conversations/" + id + "/messages"), "{\"body\":\"   \"}").cookie(guide))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MESSAGE_EMPTY"));

        mvc.perform(get("/api/conversations/" + id).cookie(welcome)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1)).andExpect(jsonPath("$.messages[0].body").value("Hallo"));
        mvc.perform(json(put("/api/conversations/" + id + "/read"), "{}").cookie(welcome)).andExpect(status().isNoContent());
        mvc.perform(get("/api/conversations/" + id).cookie(welcome)).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/api/conversations/" + id + "/messages").param("afterSeq", "0").cookie(welcome))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/conversations/" + id).cookie(login(SeedUsers.JUMPER))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CONVERSATION_MEMBER"));
        mvc.perform(get("/api/conversations/" + UUID.randomUUID()).cookie(guide)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        mvc.perform(json(post("/api/conversations/" + id + "/members/" + SeedUsers.JUMPER.id()), "").cookie(guide))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_GROUP"));
        mvc.perform(json(post("/api/conversations/lookup"), "{\"memberIds\":[\"" + UUID.randomUUID() + "\"]}")
                .cookie(guide)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void storeChatsFollowTheTeamAndCarryAdverts() throws Exception {
        Cookie bieb = login(SeedUsers.BIEB);
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String chats = body(get("/api/stores/" + S01 + "/conversations").cookie(bieb));
        String team = JsonPath.read(chats, "$.teamConversationId");
        String jumpers = JsonPath.read(chats, "$.jumperConversationId");
        assertThat(members(team, bieb)).contains(SeedUsers.BIEB.id().toString(), SeedUsers.FOODSAVER.id().toString());
        assertThat(members(jumpers, bieb)).contains(SeedUsers.BIEB.id().toString())
                .doesNotContain(SeedUsers.FOODSAVER.id().toString());
        mvc.perform(json(patch("/api/conversations/" + team), "{\"name\":\"x\"}").cookie(bieb))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONVERSATION_LOCKED"));

        mvc.perform(json(post("/api/stores/" + S01 + "/team/" + SeedUsers.FOODSAVER.id() + "/standby"),
                "{\"message\":\"Bitte pausiere ein paar Wochen.\"}").cookie(bieb)).andExpect(status().is2xxSuccessful());
        assertThat(members(team, bieb)).doesNotContain(SeedUsers.FOODSAVER.id().toString());
        assertThat(members(jumpers, bieb)).contains(SeedUsers.FOODSAVER.id().toString());
        String direct = body(json(post("/api/conversations/lookup"), "{\"memberIds\":[\"" + SeedUsers.BIEB.id() + "\"]}")
                .cookie(foodsaver));
        List<String> bodies = JsonPath.read(body(get("/api/conversations/" + JsonPath.read(direct, "$.id") + "/messages")
                .cookie(foodsaver)), "$[*].body");
        assertThat(bodies).contains("Bitte pausiere ein paar Wochen.");

        teamChat.postToTeamChat(UUID.fromString(S01), TeamChatPort.StoreChat.TEAM, "Morgen ist ein Platz frei");
        String messages = body(get("/api/conversations/" + team + "/messages").cookie(bieb));
        List<String> texts = JsonPath.read(messages, "$[*].body");
        assertThat(texts).last().isEqualTo("Morgen ist ein Platz frei");
        List<Object> senders = JsonPath.read(messages, "$[-1:].senderId");
        assertThat(senders).containsOnlyNulls();
    }

    @Test
    void seedHasConversationsWithUnreadState() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        String page = body(get("/api/conversations").cookie(guide));
        List<Integer> unread = JsonPath.read(page, "$.items[?(@.id=='00000041-0001-4000-8000-000000000003')].unreadCount");
        List<String> names = JsonPath.read(page, "$.items[?(@.id=='00000041-0001-4000-8000-000000000003')].name");
        assertThat(unread).containsExactly(1);
        assertThat(names).containsExactly("Kochgruppe Planung");
        String bot = body(get("/api/conversations").cookie(login(SeedUsers.BOT)));
        List<Integer> botUnread = JsonPath.read(bot, "$.items[?(@.id=='00000041-0001-4000-8000-000000000002')].unreadCount");
        assertThat(botUnread).containsExactly(0);
        String team = body(get("/api/conversations/00000041-0001-4000-8000-000000000004").cookie(login(SeedUsers.BIEB)));
        assertThat((String) JsonPath.read(team, "$.name")).isEqualTo("Team Coop Bahnhofbrücke");
        assertThat((Integer) JsonPath.read(team, "$.memberCount")).isGreaterThan(1);
        List<String> found = JsonPath.read(body(get("/api/users/search").param("q", "Nin").cookie(guide)), "$[*].id");
        assertThat(found).contains(SeedUsers.BIEB.id().toString());
        List<String> inGroup = JsonPath.read(body(get("/api/users/search").param("q", "Jo")
                .param("regionId", "00000011-0001-4000-8000-000000000033").cookie(guide)), "$[*].id");
        assertThat(inGroup).contains(SeedUsers.JUMPER.id().toString());
    }

    private List<String> members(String conversationId, Cookie cookie) throws Exception {
        return JsonPath.read(body(get("/api/conversations/" + conversationId).cookie(cookie)), "$.members[*].id");
    }
}
