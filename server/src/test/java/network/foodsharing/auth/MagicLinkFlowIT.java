package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import network.foodsharing.auth.repository.EmailBlocklistRepository;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.notification.mail.OutgoingMail;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.testsupport.BrowserClient;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.PostgresContainerConfig;
import network.foodsharing.testsupport.RecordingMailTransport;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Magic link end to end over HTTP: request → outbox mail → verify → fs_session (A2 backend, D8.2). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RecordingMailTransport.Config.class})
class MagicLinkFlowIT {

    @LocalServerPort
    int port;

    @Autowired
    MailOutboxService outbox;

    @Autowired
    RecordingMailTransport mails;

    @Autowired
    UserService userService;

    @Autowired
    HistoryEntryRepository historyEntries;

    @Autowired
    EmailBlocklistRepository blocklist;

    BrowserClient browser;

    @BeforeEach
    void setUp() {
        mails.clear();
        browser = new BrowserClient(port).bootstrap();
    }

    @Test
    void newUserSignsUpWithMagicLinkAndLandsInASession() {
        String email = "new.person." + UUID.randomUUID() + "@example.org";

        HttpResponse<String> requested = browser.postJson("/api/auth/magic-link",
                "{\"email\":\"" + email.toUpperCase() + "\",\"returnUrl\":\"/account/security\",\"language\":\"fr\"}");
        assertThat(requested.statusCode()).isEqualTo(202);
        assertThat(JsonPath.<String>read(requested.body(), "$.message")).isEqualTo("MAGIC_LINK_SENT");

        OutgoingMail mail = deliverMailTo(email);
        assertThat(mail.subject()).isEqualTo("Ton lien de connexion foodsharing");
        assertThat(mail.textBody()).contains("/login/verify?token=").contains("returnUrl=%2Faccount%2Fsecurity");
        assertThat(mail.htmlBody()).contains("Se connecter");

        HttpResponse<String> verified = browser.postJson("/api/auth/magic-link/verify",
                "{\"token\":\"" + RecordingMailTransport.token(mail) + "\"}");
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(verified.body(), "$.authenticated")).isTrue();
        assertThat(JsonPath.<String>read(verified.body(), "$.user.email")).isEqualTo(email);
        assertThat(JsonPath.<String>read(verified.body(), "$.user.language")).isEqualTo("fr");
        assertThat(browser.cookie("fs_session")).isPresent();

        HttpResponse<String> session = browser.get("/api/auth/session");
        assertThat(JsonPath.<Boolean>read(session.body(), "$.authenticated")).isTrue();

        User user = userService.findByEmail(email).orElseThrow();
        assertThat(actions(user.getId())).contains("ACCOUNT_CREATED", "LOGIN");
        assertThat(entry(user.getId(), "LOGIN").getMetadata()).contains("\"method\":\"EMAIL\"");

        HttpResponse<String> reused = browser.postJson("/api/auth/magic-link/verify",
                "{\"token\":\"" + RecordingMailTransport.token(mail) + "\"}");
        assertThat(reused.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(reused.body(), "$.code")).isEqualTo("LOGIN_TOKEN_EXPIRED_OR_USED");
        assertThat(actions(user.getId())).contains("LOGIN_FAILED");
    }

    @Test
    void existingUserGetsMailInTheirLanguageAndTheEmailIdentityIsLinked() {
        String email = "known." + UUID.randomUUID() + "@example.org";
        User user = userService.create(new NewUser(email, FixedClockConfig.SEED_NOW, "Known Person", null, null,
                Language.IT, null));

        assertThat(browser.postJson("/api/auth/magic-link", "{\"email\":\"" + email + "\",\"language\":\"de\"}")
                .statusCode()).isEqualTo(202);
        OutgoingMail mail = deliverMailTo(email);
        assertThat(mail.subject()).isEqualTo("Il tuo link di accesso a foodsharing");
        assertThat(mail.textBody()).startsWith("Ciao Known Person,");

        HttpResponse<String> verified = browser.postJson("/api/auth/magic-link/verify",
                "{\"token\":\"" + RecordingMailTransport.token(mail) + "\"}");
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(verified.body(), "$.user.id")).isEqualTo(user.getId().toString());
        assertThat(actions(user.getId())).contains("MAGIC_LINK_REQUESTED", "ACCOUNT_LINKED", "LOGIN");

        HttpResponse<String> methods = browser.get("/api/auth/methods");
        assertThat(methods.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(methods.body(), "$.identities[*].provider")).containsExactly("EMAIL");
    }

    @Test
    void unknownTokenIsRejected() {
        HttpResponse<String> response = browser.postJson("/api/auth/magic-link/verify", "{\"token\":\"nope\"}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("INVALID_LOGIN_TOKEN");
    }

    @Test
    void blockedDomainInvalidReturnUrlAndRateLimitAreRejected() {
        EmailBlocklistEntry entry = new EmailBlocklistEntry();
        entry.setPattern("trashmail-" + UUID.randomUUID() + ".test");
        blocklist.save(entry);
        HttpResponse<String> blocked = browser.postJson("/api/auth/magic-link",
                "{\"email\":\"someone@" + entry.getPattern() + "\"}");
        assertThat(blocked.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(blocked.body(), "$.code")).isEqualTo("EMAIL_DOMAIN_BLOCKED");

        HttpResponse<String> badReturn = browser.postJson("/api/auth/magic-link",
                "{\"email\":\"a@example.org\",\"returnUrl\":\"https://evil.example/\"}");
        assertThat(badReturn.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(badReturn.body(), "$.code")).isEqualTo("INVALID_RETURN_URL");

        String email = "flood." + UUID.randomUUID() + "@example.org";
        for (int i = 0; i < 3; i++) {
            assertThat(browser.postJson("/api/auth/magic-link", "{\"email\":\"" + email + "\"}").statusCode())
                    .isEqualTo(202);
        }
        HttpResponse<String> limited = browser.postJson("/api/auth/magic-link", "{\"email\":\"" + email + "\"}");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(JsonPath.<String>read(limited.body(), "$.code")).isEqualTo("TOO_MANY_LOGIN_REQUESTS");
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void requestWithoutCsrfTokenIsForbidden() {
        HttpResponse<String> response = browser.postJsonWithoutCsrf("/api/auth/magic-link",
                "{\"email\":\"csrf@example.org\"}");
        assertThat(response.statusCode()).isEqualTo(403);
    }

    private OutgoingMail deliverMailTo(String email) {
        outbox.processDue();
        return mails.sent().stream().filter(mail -> mail.to().equals(email)).reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No mail to " + email));
    }

    private List<String> actions(UUID userId) {
        return historyEntries.findAll().stream()
                .filter(entry -> userId.equals(entry.getSubjectId()))
                .map(HistoryEntry::getAction)
                .toList();
    }

    private HistoryEntry entry(UUID userId, String action) {
        return historyEntries.findAll().stream()
                .filter(entry -> userId.equals(entry.getSubjectId()) && action.equals(entry.getAction()))
                .findFirst().orElseThrow();
    }
}
