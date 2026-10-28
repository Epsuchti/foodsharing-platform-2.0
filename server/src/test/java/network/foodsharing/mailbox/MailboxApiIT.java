package network.foodsharing.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** MB1–MB4, MB6 on the seed: listing, unread counts, folders, read, attachments, compose/reply/forward, admin. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mailbox-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MailboxApiIT {

    static final String ZUERICH = "00000042-0001-4000-8000-000000000001";
    static final String WELCOME_BOX = "00000042-0001-4000-8000-000000000002";
    static final String MAIL_PDF = "00000042-0003-4000-8000-000000000005";
    static final String MAIL_10 = "00000042-0003-4000-8000-000000000010";
    static final String MAIL_24 = "00000042-0003-4000-8000-000000000024";
    static final String ZUERICH_STADT = "00000010-0001-4000-8000-000000000040";

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

    @Test
    void listsReadsAndMovesSeededMails() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String list = body(get("/api/mailboxes").cookie(bot));
        List<String> names = JsonPath.read(list, "$[*].emailName");
        assertThat(names).containsExactly("reto.baumann", "zuerich");
        assertThat((Integer) JsonPath.read(list, "$[1].unreadCount")).isEqualTo(3);
        assertThat((String) JsonPath.read(list, "$[1].address")).isEqualTo("zuerich@local.test");
        mvc.perform(get("/api/mailboxes/unread-count").cookie(bot)).andExpect(jsonPath("$.count").value(4));
        mvc.perform(get("/api/mailboxes/" + ZUERICH + "/folders/INBOX/mails?size=50").cookie(bot))
                .andExpect(jsonPath("$.items.length()").value(25)).andExpect(jsonPath("$.page.totalItems").value(25));
        mvc.perform(get("/api/mailboxes/" + ZUERICH + "/folders/SENT/mails").cookie(bot))
                .andExpect(jsonPath("$.items.length()").value(6));
        mvc.perform(get("/api/mailboxes/" + ZUERICH + "/folders/INBOX/mails?q=hygienekonzept").cookie(bot))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].hasAttachments").value(true));

        mvc.perform(get("/api/mailboxes/mails/" + MAIL_24 + "?keepUnread=true").cookie(bot))
                .andExpect(jsonPath("$.read").value(false));
        mvc.perform(get("/api/mailboxes/mails/" + MAIL_24).cookie(bot)).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/mailboxes/unread-count").cookie(bot)).andExpect(jsonPath("$.count").value(3));
        mvc.perform(json(patch("/api/mailboxes/mails/" + MAIL_24), "{\"read\":false}").cookie(bot))
                .andExpect(jsonPath("$.read").value(false));

        String pdf = body(get("/api/mailboxes/mails/" + MAIL_PDF).cookie(bot));
        String url = JsonPath.read(pdf, "$.attachments[0].url");
        byte[] bytes = mvc.perform(get(url).cookie(bot)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn().getResponse()
                .getContentAsByteArray();
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("Hygienekonzept");

        mvc.perform(delete("/api/mailboxes/mails/" + MAIL_24).with(csrf()).cookie(bot)).andExpect(status().isNoContent());
        mvc.perform(get("/api/mailboxes/mails/" + MAIL_24).cookie(bot)).andExpect(jsonPath("$.folder").value("TRASH"));
        mvc.perform(json(patch("/api/mailboxes/mails/" + MAIL_24), "{\"folder\":\"INBOX\"}").cookie(bot))
                .andExpect(jsonPath("$.folder").value("INBOX"));
    }

    @Test
    void membersReadOutsidersAreDenied() throws Exception {
        Cookie welcome = login(SeedUsers.WELCOME);
        List<String> names = JsonPath.read(body(get("/api/mailboxes").cookie(welcome)), "$[*].emailName");
        assertThat(names).containsExactlyInAnyOrder("welcome-zuerich", "zuerich");
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        mvc.perform(get("/api/mailboxes/" + ZUERICH + "/folders/INBOX/mails").cookie(foodsaver))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/mailboxes/mails/" + MAIL_PDF).cookie(foodsaver)).andExpect(status().isForbidden());
        mvc.perform(get("/api/regions/mailboxes").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.address == 'zuerich@local.test')]").exists());
    }

    @Test
    void composeWithAttachmentReplyAndForwardGoThroughTheOutbox() throws Exception {
        captured.clear();
        Cookie bot = login(SeedUsers.BOT);
        String upload = mvc.perform(multipart("/api/uploads").file(new MockMultipartFile("file", "plan.txt",
                "text/plain", "Plan".getBytes())).with(csrf()).cookie(bot)).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        String uploadId = JsonPath.read(upload, "$.id");
        String sent = body(json(post("/api/mailboxes/" + ZUERICH + "/mails"), "{\"to\":[\"Anna <anna@example.ch>\"],"
                + "\"cc\":[\"lukas@example.ch\"],\"subject\":\"Hallo\",\"bodyHtml\":\"<p>Hi<script>x()</script></p>\","
                + "\"attachmentUploadIds\":[\"" + uploadId + "\"]}").cookie(bot));
        assertThat((String) JsonPath.read(sent, "$.folder")).isEqualTo("SENT");
        assertThat((String) JsonPath.read(sent, "$.bodyHtml")).isEqualTo("<p>Hi</p>");
        assertThat((Integer) JsonPath.read(sent, "$.attachmentCount")).isEqualTo(1);

        String reply = body(json(post("/api/mailboxes/" + ZUERICH + "/mails"), "{\"to\":[\"lukas.meier@example.ch\"],"
                + "\"subject\":\"Re: x\",\"bodyHtml\":\"<p>ok</p>\",\"inReplyToMailId\":\"" + MAIL_10 + "\"}").cookie(bot));
        assertThat((String) JsonPath.read(reply, "$.inReplyTo")).isEqualTo("<seed-010@example.ch>");
        String forward = body(json(post("/api/mailboxes/" + ZUERICH + "/mails"), "{\"to\":[\"sara@example.ch\"],"
                + "\"subject\":\"Fwd: Hygiene\",\"bodyHtml\":\"<p>fyi</p>\",\"forwardOfMailId\":\"" + MAIL_PDF + "\"}")
                .cookie(bot));
        assertThat((Integer) JsonPath.read(forward, "$.attachmentCount")).isEqualTo(1);

        outbox.processDue();
        List<CapturedMail> anna = captured.list("anna@example.ch");
        assertThat(anna).hasSize(1);
        assertThat(anna.get(0).from()).contains("zuerich@local.test");
        assertThat(anna.get(0).attachmentNames()).containsExactly("plan.txt");
        assertThat(captured.list("lukas@example.ch")).hasSize(1);
        assertThat(captured.list("sara@example.ch").get(0).attachmentNames())
                .containsExactly("Hygienekonzept-Fairteiler.pdf");

        mvc.perform(json(post("/api/mailboxes/" + ZUERICH + "/mails"), "{\"to\":[\"not an address\"],\"subject\":\"x\","
                + "\"bodyHtml\":\"x\"}").cookie(bot)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RECIPIENT"));
    }

    @Test
    @Order(Integer.MAX_VALUE - 1)
    void injectedIncomingMailLandsInTheInboxAndBouncesFlagTheSentMail() throws Exception {
        Cookie bot = login(SeedUsers.BOT);
        String raw = "From: Anna <anna@example.ch>\nTo: zuerich@local.test\nSubject: Inbound-Test\n"
                + "Message-ID: <inbound-1@example.ch>\n\nHallo Postfach\n";
        mvc.perform(json(post("/api/dev/mailbox/incoming"), "{\"rawMime\":" + quote(raw) + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/mailboxes/" + ZUERICH + "/folders/INBOX/mails?q=Inbound-Test").cookie(bot))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(get("/api/dev/notifications").param("userId", SeedUsers.REPORTS.id().toString())
                .param("type", "MAILBOX_NEW_MAIL")).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers
                .greaterThanOrEqualTo(1)));

        String dsn = "From: MAILER-DAEMON@mx.example.ch\nTo: zuerich@local.test\nSubject: Undelivered\n"
                + "Content-Type: multipart/report; report-type=delivery-status; boundary=\"B\"\n\n--B\n"
                + "Content-Type: text/plain\n\nfailed\n--B\nContent-Type: message/delivery-status\n\n"
                + "Final-Recipient: rfc822; x@example.ch\nAction: failed\n--B\nContent-Type: text/rfc822-headers\n\n"
                + "Message-ID: <seed-sent-027@local.test>\n--B--\n";
        mvc.perform(json(post("/api/dev/mailbox/incoming"), "{\"rawMime\":" + quote(dsn) + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/mailboxes/mails/00000042-0003-4000-8000-000000000027").cookie(bot))
                .andExpect(jsonPath("$.bounce").value(true));
        mvc.perform(post("/api/dev/jobs/mailbox-fetcher/run").with(csrf())).andExpect(status().is2xxSuccessful());
        mvc.perform(json(post("/api/public/mailbox/inbound/wrong"), "{\"rawMime\":\"eA==\"}"))
                .andExpect(status().isNotFound());
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @Order(Integer.MAX_VALUE)
    void ambassadorsCreateMailboxesAndManageMembers() throws Exception {
        Cookie welcome = login(SeedUsers.WELCOME);
        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(json(post("/api/regions/" + ZUERICH_STADT + "/mailbox"), "{\"emailName\":\"zweite\"}").cookie(bot))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAILBOX_ALREADY_EXISTS"));
        String group = "00000011-0001-4000-8000-000000000033";
        mvc.perform(json(post("/api/regions/" + group + "/mailbox"), "{\"emailName\":\"zuerich\"}").cookie(bot))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_NAME_TAKEN"));
        mvc.perform(json(post("/api/regions/" + group + "/mailbox"), "{\"emailName\":\"postmaster\"}").cookie(bot))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_NAME_RESERVED"));
        mvc.perform(json(post("/api/regions/" + group + "/mailbox"), "{\"emailName\":\"kochgruppe\"}")
                .cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/regions/" + group + "/mailbox"), "{\"emailName\":\"kochgruppe\"}").cookie(bot))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.address").value("kochgruppe@local.test"));

        mvc.perform(json(put("/api/mailboxes/" + ZUERICH + "/members"), "{\"userIds\":[\"" + SeedUsers.REPORTS.id()
                + "\"]}").cookie(bot)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/mailboxes/" + ZUERICH + "/folders/INBOX/mails").cookie(welcome))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/mailboxes/" + ZUERICH + "/members"), "{\"userIds\":[\"" + SeedUsers.FOODSAVER.id()
                + "\"]}").cookie(bot)).andExpect(status().isBadRequest());

        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(json(post("/api/users/" + SeedUsers.FOODSAVER.id() + "/mailbox"), "{\"emailName\":\"fs\"}")
                .cookie(orga)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MAILBOX_NOT_ALLOWED"));
        mvc.perform(json(post("/api/users/" + SeedUsers.WELCOME.id() + "/mailbox"), "{\"emailName\":\"priya.sharma\"}")
                .cookie(orga)).andExpect(status().isCreated());
        List<String> names = JsonPath.read(body(get("/api/mailboxes").cookie(welcome)), "$[*].emailName");
        assertThat(names).containsExactly("priya.sharma", "welcome-zuerich");
        mvc.perform(delete("/api/mailboxes/" + WELCOME_BOX).with(csrf()).cookie(orga)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAILBOX_NOT_EMPTY"));
        mvc.perform(delete("/api/mailboxes/" + WELCOME_BOX + "?force=true").with(csrf()).cookie(orga))
                .andExpect(status().isNoContent());
    }
}
