package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Email change (A5): request → two mails → confirm → login identity updated, other sessions revoked; aborts. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email-change-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class EmailChangeFlowIT {

    private static final Pattern TOKEN = Pattern.compile("token=([^\\s\"&<]+)");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MailOutboxRepository outbox;

    @Autowired
    private AuthIdentityRepository identities;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String tokenMailedTo(String address, String template) {
        MailOutboxMessage mail = outbox.findAll().stream()
                .filter(m -> address.equals(m.getToAddress()) && template.equals(m.getTemplate()))
                .reduce((first, second) -> second).orElseThrow();
        Matcher matcher = TOKEN.matcher(mail.getTextBody());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void requestConfirmUpdatesIdentityAndRevokesOtherSessions() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        Cookie otherDevice = login(SeedUsers.JUMPER);
        String email = "/api/users/me/email";
        mvc.perform(json(post(email), "{\"newEmail\":\"kein-mail\"}").cookie(jumper))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_INVALID"));
        mvc.perform(json(post(email), "{\"newEmail\":\"jonas@mailinator.com\"}").cookie(jumper))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_BLOCKED"));
        mvc.perform(json(post(email), "{\"newEmail\":\"" + SeedUsers.BIEB.email() + "\"}").cookie(jumper))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(json(post(email), "{\"newEmail\":\"Jonas.Neu@Example.org\"}").cookie(jumper))
                .andExpect(status().isAccepted());
        mvc.perform(get(email).cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEmail").value("jonas.neu@example.org"));

        String token = tokenMailedTo("jonas.neu@example.org", "user/email-change-confirm");
        assertThat(tokenMailedTo(SeedUsers.JUMPER.email(), "user/email-change-notice")).isNotBlank();
        mvc.perform(json(post(email + "/confirm"), "{\"token\":\"" + token + "\"}").cookie(login(SeedUsers.BIEB)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_CHANGE_TOKEN_INVALID"));
        mvc.perform(json(post(email + "/confirm"), "{\"token\":\"" + token + "\"}").cookie(jumper))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jonas.neu@example.org"));
        mvc.perform(get("/api/users/me").cookie(otherDevice)).andExpect(status().isUnauthorized());
        assertThat(identities.findByUserId(SeedUsers.JUMPER.id()).stream()
                .filter(i -> i.getProvider() == IdentityProvider.EMAIL)
                .allMatch(i -> i.getSubject().equals("jonas.neu@example.org"))).isTrue();
        mvc.perform(json(post(email + "/confirm"), "{\"token\":\"" + token + "\"}").cookie(jumper))
                .andExpect(status().isBadRequest());
        mvc.perform(get(email).cookie(jumper)).andExpect(jsonPath("$.pendingEmail").doesNotExist());
    }

    @Test
    void adminRequestAndAbortWithTheMailedLink() throws Exception {
        String admin = "/api/admin/users/" + SeedUsers.GUIDE.id() + "/email";
        mvc.perform(json(post(admin), "{\"newEmail\":\"julien@example.org\"}").cookie(login(SeedUsers.JUMPER)))
                .andExpect(status().isForbidden());
        mvc.perform(json(post(admin), "{\"newEmail\":\"julien@example.org\"}").cookie(login(SeedUsers.ORGA)))
                .andExpect(status().isAccepted());
        String abort = tokenMailedTo(SeedUsers.GUIDE.email(), "user/email-change-notice");
        mvc.perform(json(post("/api/public/users/email-change/abort"), "{\"token\":\"" + abort + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/public/users/email-change/abort"), "{\"token\":\"" + abort + "\"}"))
                .andExpect(status().isBadRequest());
        Cookie guide = login(SeedUsers.GUIDE);
        mvc.perform(get("/api/users/me/email").cookie(guide)).andExpect(jsonPath("$.pendingEmail").doesNotExist());

        mvc.perform(json(post("/api/users/me/email"), "{\"newEmail\":\"julien2@example.org\"}").cookie(guide))
                .andExpect(status().isAccepted());
        mvc.perform(delete("/api/users/me/email").with(csrf()).cookie(guide)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/email").cookie(guide)).andExpect(jsonPath("$.pendingEmail").doesNotExist());
        String confirm = tokenMailedTo("julien2@example.org", "user/email-change-confirm");
        mvc.perform(json(post("/api/users/me/email/confirm"), "{\"token\":\"" + confirm + "\"}").cookie(guide))
                .andExpect(status().isBadRequest());
    }
}
