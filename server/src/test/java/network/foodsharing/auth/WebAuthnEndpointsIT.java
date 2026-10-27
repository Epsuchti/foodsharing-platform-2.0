package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.service.LoginService;
import network.foodsharing.testsupport.BrowserClient;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.PostgresContainerConfig;
import network.foodsharing.testsupport.RecordingMailTransport;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Wiring of the Spring Security WebAuthn endpoints in the main chain: registration options need an fs_session
 * (and show email / display name), authentication options are public, a bad assertion is a JSON 401, the
 * framework's credential DELETE is disabled, CSRF applies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RecordingMailTransport.Config.class})
class WebAuthnEndpointsIT {

    @LocalServerPort
    int port;

    @Autowired
    UserService userService;

    @Autowired
    LoginService loginService;

    @Test
    void registrationOptionsNeedASessionAndDescribeTheUser() {
        BrowserClient anonymous = new BrowserClient(port).bootstrap();
        // Spring's PublicKeyCredentialCreationOptionsFilter runs before authorization and answers 400 itself.
        assertThat(anonymous.postJson("/webauthn/register/options", "").statusCode()).isEqualTo(400);
        assertThat(anonymous.postJson("/webauthn/register", "{}").statusCode()).isEqualTo(401);

        User user = userService.create(new NewUser("webauthn." + UUID.randomUUID() + "@example.org",
                FixedClockConfig.SEED_NOW, "Web Authn", null, null, Language.DE, null));
        BrowserClient browser = signedIn(user);

        assertThat(browser.postJsonWithoutCsrf("/webauthn/register/options", "").statusCode()).isEqualTo(403);
        HttpResponse<String> options = browser.postJson("/webauthn/register/options", "");
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(options.body(), "$.rp.id")).isEqualTo("localhost");
        assertThat(JsonPath.<String>read(options.body(), "$.rp.name")).isEqualTo("foodsharing");
        assertThat(JsonPath.<String>read(options.body(), "$.user.name")).isEqualTo(user.getEmail());
        assertThat(JsonPath.<String>read(options.body(), "$.user.displayName")).isEqualTo("Web Authn");
        assertThat(JsonPath.<String>read(options.body(), "$.challenge")).isNotBlank();
        assertThat(JsonPath.<String>read(options.body(), "$.authenticatorSelection.residentKey")).isEqualTo("required");

        HttpResponse<String> badRegistration = browser.postJson("/webauthn/register", "{\"publicKey\":{}}");
        assertThat(badRegistration.statusCode()).isEqualTo(400);

        assertThat(browser.delete("/webauthn/register/AAAA").statusCode()).isEqualTo(403);
    }

    @Test
    void authenticationOptionsArePublicAndBadAssertionsAreRejectedAsJson() {
        BrowserClient browser = new BrowserClient(port).bootstrap();
        HttpResponse<String> options = browser.postJson("/webauthn/authenticate/options", "");
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(options.body(), "$.rpId")).isEqualTo("localhost");
        assertThat(JsonPath.<String>read(options.body(), "$.challenge")).isNotBlank();

        HttpResponse<String> login = browser.postJson("/login/webauthn", "{\"id\":\"AAAA\"}");
        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(login.body(), "$.code")).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(browser.cookie("fs_session")).isEmpty();
    }

    /** A browser holding a real fs_session cookie for the user. */
    private BrowserClient signedIn(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            loginService.login(user.getId(), LoginMethod.DEV, request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
        String setCookie = response.getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith("fs_session=")).findFirst().orElseThrow();
        String token = setCookie.substring("fs_session=".length(), setCookie.indexOf(';'));
        BrowserClient browser = new BrowserClient(port).bootstrap();
        browser.setCookie("fs_session", token);
        return browser;
    }
}
