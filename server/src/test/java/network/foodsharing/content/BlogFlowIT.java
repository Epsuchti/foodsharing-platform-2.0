package network.foodsharing.content;

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
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.upload.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** B11: public CMS endpoints on seed data, blog check/publish flow, content page creation, support form. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class BlogFlowIT {

    private static final String WELCOME_GROUP = "00000011-0001-4000-8000-000000000020";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UploadService uploads;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void publicPagesServeTheSeed() throws Exception {
        mvc.perform(get("/api/public/blog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].title").value("Rekord: 10 Tonnen in einem Monat"))
                .andExpect(jsonPath("$.page.totalItems").value(6));
        mvc.perform(get("/api/public/faq").param("lang", "fr")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].question").value("foodsharing coûte-t-il quelque chose ?"));
        mvc.perform(get("/api/public/team")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/public/release-notes").param("lang", "en")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].version").value("5.0.0"));
        mvc.perform(get("/api/public/petition")).andExpect(status().isOk())
                .andExpect(jsonPath("$.href").value("https://petition.local.test/foodsharing"));
        mvc.perform(get("/api/public/donations").param("lang", "it")).andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignPages.length()").value(2))
                .andExpect(jsonPath("$.showModal").value(false));
        mvc.perform(get("/api/public/content/imprint").param("lang", "gws")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Impressum"));
        String unpublished = "00000012-0006-4000-8000-000000000008";
        mvc.perform(get("/api/public/blog/" + unpublished)).andExpect(status().isNotFound());
    }

    @Test
    void ambassadorRequestsCheckAndEditorPublishes() throws Exception {
        Cookie welcome = login(SeedUsers.WELCOME);
        String created = mvc.perform(post("/api/blog").with(csrf()).cookie(welcome)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Willkommen\",\"teaser\":\"Kurz\",\"body\":\"Text\",\"regionId\":\""
                                + WELCOME_GROUP + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mayPublish").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        // a global post needs an editor
        mvc.perform(post("/api/blog").with(csrf()).cookie(welcome).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Global\",\"teaser\":\"\",\"body\":\"Text\"}")).andExpect(status().isForbidden());

        mvc.perform(put("/api/blog/" + id + "/published").with(csrf()).cookie(welcome)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.awaitingCheck").value(true));
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'BLOG_NEW_CHECK' and deleted_at is null",
                Integer.class, SeedUsers.BOT.id())).isEqualTo(1);
        mvc.perform(get("/api/public/blog/" + id)).andExpect(status().isNotFound());

        Cookie bot = login(SeedUsers.BOT);
        mvc.perform(put("/api/blog/" + id + "/published").with(csrf()).cookie(bot)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.awaitingCheck").value(false));
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'BLOG_NEW_CHECK' and deleted_at is null",
                Integer.class, SeedUsers.BOT.id())).isZero();
        mvc.perform(get("/api/public/blog/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/public/blog").param("regionId", WELCOME_GROUP))
                .andExpect(jsonPath("$.items.length()").value(1));

        mvc.perform(get("/api/blog/" + id).cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mayEdit").value(false));
        mvc.perform(delete("/api/blog/" + id).with(csrf()).cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/blog/" + id).with(csrf()).cookie(bot))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/public/blog/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void deletedPostLosesItsPictureOnCleanup() throws Exception {
        String post = "00000012-0006-4000-8000-000000000005";
        String picture = "00000040-0001-4000-8000-000000000405";
        mvc.perform(delete("/api/blog/" + post).with(csrf()).cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isNoContent());
        uploads.deleteOwnerless();
        assertThat(jdbc.queryForObject("select count(*) from upload where id = ?", Integer.class, picture)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from upload where id = ?", Integer.class,
                "00000040-0001-4000-8000-000000000401")).isEqualTo(1);
        // other tests count the published seed posts
        jdbc.update("update blog_post set deleted_at = null, published = true where id = ?", post);
    }

    @Test
    void orgaCreatesPageAndPrAdminMayNotChooseAnotherEditorFunction() throws Exception {
        mvc.perform(post("/api/content/pages").with(csrf()).cookie(login(SeedUsers.ORGA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"Neue-Seite\",\"kind\":\"PAGE\",\"title\":\"Neu\",\"body\":\"Text\","
                                + "\"editorFunction\":\"IT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/content/pages").with(csrf()).cookie(login(SeedUsers.ORGA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"Neue-Seite\",\"kind\":\"PAGE\",\"title\":\"Neu\",\"body\":\"Text\","
                                + "\"editorFunction\":\"BOARD\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("neue-seite"));
        mvc.perform(post("/api/content/pages").with(csrf()).cookie(login(SeedUsers.BOT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"neue-seite\",\"kind\":\"PAGE\",\"title\":\"Neu\",\"body\":\"Text\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/content/pages/neue-seite").cookie(login(SeedUsers.BOT)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/content/pages").with(csrf()).cookie(login(SeedUsers.BOT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"bot-seite\",\"kind\":\"PAGE\",\"title\":\"Neu\",\"body\":\"Text\","
                                + "\"editorFunction\":\"BOARD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSupportTicketIsMailedAndRateLimited() throws Exception {
        String ticket = "{\"subject\":\"Hilfe\",\"body\":\"Bitte helfen\",\"email\":\"gast@example.ch\","
                + "\"firstName\":\"Gast\",\"page\":\"/content/faq\",\"userAgent\":\"JUnit\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/public/support/tickets").with(csrf()).with(request -> {
                        request.setRemoteAddr("203.0.113.9");
                        return request;
                    }).contentType(MediaType.APPLICATION_JSON).content(ticket))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.ticketId").exists());
        }
        mvc.perform(post("/api/public/support/tickets").with(csrf()).with(request -> {
                    request.setRemoteAddr("203.0.113.9");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content(ticket))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SUPPORT_RATE_LIMITED"));
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox where subject like 'Support-Anfrage: Hilfe%'"
                + " and text_body like '%JUnit%'", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from bell where user_id = ? and type = 'SUPPORT_TICKET' and deleted_at is null",
                Integer.class, SeedUsers.ORGA.id())).isEqualTo(1);
    }
}
