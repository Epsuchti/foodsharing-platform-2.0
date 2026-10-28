package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** M6 B3 account pieces: photo (A3), admin notes (A7), bounce handling (A18), info mail / mention settings (A20). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class AccountFlowIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private network.foodsharing.user.service.UserService users;

    @Autowired
    private network.foodsharing.notification.mail.service.MailOutboxService outbox;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String upload(Cookie user, String name, String mime) throws Exception {
        String body = mvc.perform(multipart("/api/uploads").file(new MockMultipartFile("file", name, mime,
                        new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3})).with(csrf()).cookie(user))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void photoIsSetReplacedAndRemoved() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        String text = upload(guide, "notes.txt", "text/plain");
        mvc.perform(json(put("/api/users/me/photo"), "{\"uploadId\":\"" + text + "\"}").cookie(guide))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UPLOAD_NOT_IMAGE"));
        String first = upload(guide, "me.png", "image/png");
        mvc.perform(json(put("/api/users/me/photo"), "{\"uploadId\":\"" + first + "\"}").cookie(login(SeedUsers.BIEB)))
                .andExpect(status().isForbidden());
        mvc.perform(json(put("/api/users/me/photo"), "{\"uploadId\":\"" + first + "\"}").cookie(guide))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").value("/api/uploads/" + first + "?size=md"));
        mvc.perform(get("/api/uploads/" + first).cookie(login(SeedUsers.NEWBIE))).andExpect(status().isOk());
        String second = upload(guide, "me2.png", "image/png");
        mvc.perform(json(put("/api/users/me/photo"), "{\"uploadId\":\"" + second + "\"}").cookie(guide))
                .andExpect(status().isOk());
        mvc.perform(get("/api/uploads/" + first).cookie(guide)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/me/photo").with(csrf()).cookie(guide)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").cookie(guide)).andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void adminNotesWall() throws Exception {
        String notes = "/api/admin/users/" + SeedUsers.JUMPER.id() + "/notes";
        Cookie orga = login(SeedUsers.ORGA);
        String created = mvc.perform(json(post(notes), "{\"body\":\"Hat zweimal kurzfristig abgesagt.\"}").cookie(orga))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mayDelete").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get(notes).cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].author.id").value(SeedUsers.ORGA.id().toString()));
        mvc.perform(get(notes).cookie(login(SeedUsers.GUIDE))).andExpect(status().isForbidden());
        mvc.perform(delete(notes + "/" + id).with(csrf()).cookie(orga)).andExpect(status().isNoContent());
        mvc.perform(delete(notes + "/" + id).with(csrf()).cookie(orga)).andExpect(status().isNotFound());
    }

    @Test
    void bounceAndMailSettings() throws Exception {
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(delete("/api/users/" + SeedUsers.GUIDE.id() + "/email-bounce").with(csrf()).cookie(guide))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/users/" + SeedUsers.BIEB.id() + "/email-bounce").with(csrf()).cookie(guide))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/bounced").cookie(guide)).andExpect(status().isForbidden());
        String page = mvc.perform(get("/api/admin/users/bounced").cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(page, "$.items")).isNotNull();

        mvc.perform(json(patch("/api/users/me/settings"), "{\"mentionNotifications\":false,\"infoMailMessage\":false}")
                .cookie(guide)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionNotifications").value(false))
                .andExpect(jsonPath("$.infoMailMessage").value(false));
    }

    @Test
    void mailsToBouncedAddressesAreSuppressedUntilCleared() throws Exception {
        String email = "bieb@local";
        users.markEmailBounced(email, java.time.Instant.parse("2026-11-01T10:00:00Z"));
        var draft = new network.foodsharing.notification.mail.service.MailDraft(email, "forum-new-post",
                network.foodsharing.api.model.Language.DE, java.util.Map.of(), SeedUsers.BIEB.id(), "FORUM", null);
        assertThat(outbox.enqueue(draft)).isNull();
        String page = mvc.perform(get("/api/admin/users/bounced").cookie(login(SeedUsers.ORGA)))
                .andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(page, "$.items[*].user.id"))
                .contains(SeedUsers.BIEB.id().toString());
        mvc.perform(delete("/api/users/" + SeedUsers.BIEB.id() + "/email-bounce").with(csrf())
                .cookie(login(SeedUsers.BIEB))).andExpect(status().isNoContent());
        page = mvc.perform(get("/api/admin/users/bounced").cookie(login(SeedUsers.ORGA)))
                .andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(page, "$.items[*].user.id"))
                .doesNotContain(SeedUsers.BIEB.id().toString());
    }
}
