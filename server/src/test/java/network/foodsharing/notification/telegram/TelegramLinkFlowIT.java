package network.foodsharing.notification.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import org.hamcrest.Matchers;
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
 * N5/A28 on the seed (e2e profile, Telegram in log mode): link token → {@code /api/dev/telegram/incoming} → linked →
 * test message captured; reused token, {@code /stop}; alert chats with permissions; plus the B3 admin endpoints
 * (mail outbox list/retry, integration test message) and the cleanup job.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:telegram-flow-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class TelegramLinkFlowIT {

    static final String ZUERICH_STADT = "00000010-0001-4000-8000-000000000040";
    static final String S01 = "00000020-0001-4000-8000-000000000001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void incoming(String chatId, String text) throws Exception {
        mvc.perform(json(post("/api/dev/telegram/incoming"), "{\"chatId\":\"" + chatId + "\",\"text\":\"" + text + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String chatOf(SeedUsers user) {
        return jdbc.queryForObject("select telegram_chat_id from user_account where id = ?", String.class, user.id());
    }

    @Test
    void linkTestAndStop() throws Exception {
        mvc.perform(post("/api/telegram/test").with(TestAuth.as(SeedUsers.FOODSAVER)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TELEGRAM_NOT_LINKED"));
        String body = mvc.perform(post("/api/telegram/link-token").with(TestAuth.as(SeedUsers.FOODSAVER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deepLink").value(Matchers.startsWith("https://t.me/foodsharing_dev_bot?start=")))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        incoming("555001", "/start " + token);
        assertThat(chatOf(SeedUsers.FOODSAVER)).isEqualTo("555001");
        mvc.perform(get("/api/dev/notifications?channel=BELL&type=TELEGRAM_LINKED&userId=" + SeedUsers.FOODSAVER.id()))
                .andExpect(jsonPath("$.length()").value(1));

        // reused token: rejected, the chat stays with foodsaver
        incoming("555002", "/start " + token);
        assertThat(chatOf(SeedUsers.FOODSAVER)).isEqualTo("555001");
        assertThat(jdbc.queryForObject("select count(*) from user_account where telegram_chat_id = '555002'",
                Integer.class)).isZero();

        mvc.perform(post("/api/telegram/test").with(TestAuth.as(SeedUsers.FOODSAVER)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/dev/notifications?channel=TELEGRAM&userId=" + SeedUsers.FOODSAVER.id()))
                .andExpect(jsonPath("$[0].body").value(Matchers.containsString("Telegram")));

        incoming("555001", "/stop");
        assertThat(chatOf(SeedUsers.FOODSAVER)).isNull();
        mvc.perform(delete("/api/telegram/link").with(TestAuth.as(SeedUsers.FOODSAVER)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TELEGRAM_NOT_LINKED"));

        // seed: bieb is linked
        assertThat(chatOf(SeedUsers.BIEB)).isEqualTo("100000001");
        mvc.perform(delete("/api/telegram/link").with(TestAuth.as(SeedUsers.BIEB)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(chatOf(SeedUsers.BIEB)).isNull();
    }

    @Test
    void alertChatsWithPermissions() throws Exception {
        mvc.perform(json(post("/api/dev/telegram/incoming"),
                "{\"chatId\":\"-1009\",\"text\":\"hello\",\"title\":\"Fairteiler Gruppe\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/telegram/chats").with(TestAuth.as(SeedUsers.ORGA)))
                .andExpect(jsonPath("$[?(@.chatId == '-1009')].title").value("Fairteiler Gruppe"));
        mvc.perform(get("/api/telegram/chats").with(TestAuth.as(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());

        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/telegram-chats").with(TestAuth.as(SeedUsers.ORGA)))
                .andExpect(jsonPath("$.chats[0].title").value("Zürich Notfallchat"));
        mvc.perform(get("/api/regions/" + ZUERICH_STADT + "/telegram-chats").with(TestAuth.as(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/stores/" + S01 + "/telegram-chats").with(TestAuth.as(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());

        mvc.perform(json(put("/api/stores/" + S01 + "/telegram-chats").with(TestAuth.as(SeedUsers.ORGA)),
                        "{\"chats\":[{\"chatId\":\"-1009\",\"title\":\"Fairteiler Gruppe\",\"enabled\":true}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chats.length()").value(1))
                .andExpect(jsonPath("$.chats[0].chatId").value("-1009"));
        mvc.perform(json(post("/api/stores/" + S01 + "/telegram-chats/test").with(TestAuth.as(SeedUsers.ORGA)),
                "{\"chatId\":\"-1009\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/dev/notifications?channel=TELEGRAM"))
                .andExpect(jsonPath("$[0].target").value("-1009"));
        mvc.perform(json(post("/api/stores/" + S01 + "/telegram-chats/test").with(TestAuth.as(SeedUsers.ORGA)),
                "{\"chatId\":\"-1002\"}")).andExpect(status().isNotFound());
    }

    @Test
    void adminOutboxRetryAndIntegrationTest() throws Exception {
        UUID failed = UUID.randomUUID();
        jdbc.update("insert into mail_outbox (id, version, created_at, updated_at, to_address, subject, text_body, "
                + "language, status, attempts, next_attempt_at, last_error, template, priority) values (?, 0, "
                + "current_timestamp, current_timestamp, 'x@example.org', 'Hallo', 'Text', 'DE', 'FAILED', 8, "
                + "current_timestamp, 'boom', 'pickup/removed', 0)", failed);
        mvc.perform(get("/api/admin/mail-outbox?status=FAILED&search=example.org").with(TestAuth.as(SeedUsers.ORGA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(failed.toString()))
                .andExpect(jsonPath("$.items[0].lastError").value("boom"));
        mvc.perform(get("/api/admin/mail-outbox").with(TestAuth.as(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/mail-outbox/" + failed + "/retry").with(TestAuth.as(SeedUsers.ORGA)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0));
        mvc.perform(post("/api/admin/mail-outbox/" + UUID.randomUUID() + "/retry").with(TestAuth.as(SeedUsers.ORGA))
                .with(csrf())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MAIL_NOT_FOUND"));

        mvc.perform(json(post("/api/admin/integrations/TELEGRAM/test").with(TestAuth.as(SeedUsers.ORGA)),
                "{\"target\":\"-4242\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.integration").value("TELEGRAM"));
        mvc.perform(get("/api/dev/notifications?channel=TELEGRAM")).andExpect(jsonPath("$[0].target").value("-4242"));
        mvc.perform(json(post("/api/admin/integrations/TELEGRAM/test").with(TestAuth.as(SeedUsers.ORGA)), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cleanupJobRuns() throws Exception {
        jdbc.update("insert into telegram_link_token (id, version, created_at, updated_at, token, user_id, expires_at) "
                + "values (?, 0, current_timestamp, current_timestamp, 'expired-token', ?, ?)", UUID.randomUUID(),
                SeedUsers.FOODSAVER.id(), java.sql.Timestamp.valueOf("2020-01-01 00:00:00"));
        mvc.perform(post("/api/dev/jobs/cleanup/run").with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from telegram_link_token where token = 'expired-token'",
                Integer.class)).isZero();
    }

    @Test
    void simulatedOutageBellsOrgaOnce() throws Exception {
        mvc.perform(post("/api/dev/integrations/HOLIDAY_CALENDAR/fail").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/dev/integrations/HOLIDAY_CALENDAR/fail").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/dev/notifications?channel=BELL&type=INTEGRATION_DOWN&userId=" + SeedUsers.ORGA.id()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/integrations").with(TestAuth.as(SeedUsers.ORGA)))
                .andExpect(jsonPath("$[?(@.integration == 'HOLIDAY_CALENDAR')].state").value("OPEN"));
    }
}
