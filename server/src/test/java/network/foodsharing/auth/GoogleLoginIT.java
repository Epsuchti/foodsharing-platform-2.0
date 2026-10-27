package network.foodsharing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.Language;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.testsupport.BrowserClient;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.OidcProviderStub;
import network.foodsharing.testsupport.PostgresContainerConfig;
import network.foodsharing.testsupport.RecordingMailTransport;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Google login end to end against an in-test OIDC provider ({@link OidcProviderStub}): authorization redirect with
 * return URL, callback, token + JWKS + userinfo, account creation / linking by verified email, fs_session cookie,
 * history entries.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RecordingMailTransport.Config.class})
class GoogleLoginIT {

    static final OidcProviderStub PROVIDER = new OidcProviderStub();

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> OidcProviderStub.CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.google.client-secret",
                () -> OidcProviderStub.CLIENT_SECRET);
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri",
                () -> PROVIDER.issuer() + "/authorize");
        registry.add("spring.security.oauth2.client.provider.google.token-uri", () -> PROVIDER.issuer() + "/token");
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri", () -> PROVIDER.issuer() + "/jwks");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri",
                () -> PROVIDER.issuer() + "/userinfo");
        registry.add("app.auth.google.issuer", PROVIDER::issuer);
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    UserService userService;

    @Autowired
    HistoryEntryRepository historyEntries;

    @Test
    void firstGoogleLoginCreatesTheAccountAndASecondOneLogsIn() {
        String email = "gina." + UUID.randomUUID() + "@gmail.test";
        String subject = "google-" + UUID.randomUUID();
        BrowserClient browser = new BrowserClient(port);

        HttpResponse<String> callback = loginWithGoogle(browser, "/account/security",
                claims(subject, email, true, "Gina", "Gärtner", "fr-CH"));
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(location(callback)).endsWith("/account/security");
        assertThat(browser.cookie("fs_session")).isPresent();

        HttpResponse<String> session = browser.get("/api/auth/session");
        assertThat(JsonPath.<Boolean>read(session.body(), "$.authenticated")).isTrue();
        assertThat(JsonPath.<String>read(session.body(), "$.user.email")).isEqualTo(email);
        assertThat(JsonPath.<String>read(session.body(), "$.user.displayName")).isEqualTo("Gina Gärtner");
        assertThat(JsonPath.<String>read(session.body(), "$.user.language")).isEqualTo("fr");

        User user = userService.findByEmail(email).orElseThrow();
        assertThat(user.getFirstName()).isEqualTo("Gina");
        assertThat(user.getEmailVerifiedAt()).isNotNull();
        assertThat(actions(user.getId())).containsExactlyInAnyOrder("ACCOUNT_CREATED", "LOGIN");
        assertThat(entry(user.getId(), "LOGIN").getMetadata()).contains("\"method\":\"GOOGLE\"");

        HttpResponse<String> methods = browser.get("/api/auth/methods");
        assertThat(JsonPath.<List<String>>read(methods.body(), "$.identities[*].provider")).containsExactly("GOOGLE");

        BrowserClient secondDevice = new BrowserClient(port);
        HttpResponse<String> again = loginWithGoogle(secondDevice, null,
                claims(subject, email, true, "Gina", "Gärtner", "fr-CH"));
        assertThat(location(again)).endsWith("/");
        assertThat(secondDevice.cookie("fs_session")).isPresent();
        assertThat(actions(user.getId())).containsExactlyInAnyOrder("ACCOUNT_CREATED", "LOGIN", "LOGIN");
    }

    @Test
    void googleLoginLinksAnExistingAccountWithTheSameVerifiedEmail() {
        String email = "existing." + UUID.randomUUID() + "@example.org";
        User existing = userService.create(new NewUser(email, FixedClockConfig.SEED_NOW, "Existing", null, null,
                Language.DE, null));
        BrowserClient browser = new BrowserClient(port);

        HttpResponse<String> callback = loginWithGoogle(browser, "/dashboard",
                claims("google-" + UUID.randomUUID(), email.toUpperCase(), true, "Other", "Name", "en"));
        assertThat(location(callback)).endsWith("/dashboard");

        HttpResponse<String> session = browser.get("/api/auth/session");
        assertThat(JsonPath.<String>read(session.body(), "$.user.id")).isEqualTo(existing.getId().toString());
        assertThat(JsonPath.<String>read(session.body(), "$.user.displayName")).isEqualTo("Existing");
        assertThat(actions(existing.getId())).containsExactlyInAnyOrder("ACCOUNT_CREATED", "ACCOUNT_LINKED", "LOGIN");
    }

    @Test
    void unverifiedGoogleEmailIsRejected() {
        String email = "unverified." + UUID.randomUUID() + "@gmail.test";
        BrowserClient browser = new BrowserClient(port);

        HttpResponse<String> callback = loginWithGoogle(browser, "/account",
                claims("google-" + UUID.randomUUID(), email, false, "Una", "Verified", "de"));
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(location(callback)).endsWith("/login?error=GOOGLE_EMAIL_NOT_VERIFIED&returnUrl=%2Faccount");
        assertThat(browser.cookie("fs_session")).isEmpty();
        assertThat(userService.findByEmail(email)).isEmpty();
    }

    @Test
    void unsafeReturnUrlFallsBackToTheDefaultPage() {
        BrowserClient browser = new BrowserClient(port);
        HttpResponse<String> callback = loginWithGoogle(browser, "https://evil.example/phish",
                claims("google-" + UUID.randomUUID(), "safe." + UUID.randomUUID() + "@gmail.test", true, "Sam",
                        "Safe", "it"));
        assertThat(location(callback)).doesNotContain("evil").endsWith("/");
    }

    /** Starts at /oauth2/authorization/google, plays the provider's authorize step, returns the callback response. */
    private HttpResponse<String> loginWithGoogle(BrowserClient browser, String returnUrl, Map<String, Object> claims) {
        String start = "/oauth2/authorization/google"
                + (returnUrl == null ? "" : "?returnUrl=" + java.net.URLEncoder.encode(returnUrl, StandardCharsets.UTF_8));
        HttpResponse<String> authorize = browser.get(start);
        assertThat(authorize.statusCode()).isEqualTo(302);
        URI authorizeUri = URI.create(location(authorize));
        assertThat(authorizeUri.toString()).startsWith(PROVIDER.issuer() + "/authorize");
        Map<String, String> query = query(authorizeUri);
        assertThat(query).containsEntry("client_id", OidcProviderStub.CLIENT_ID);
        assertThat(query.get("scope")).contains("openid", "email", "profile");

        Map<String, Object> idTokenClaims = new LinkedHashMap<>(claims);
        idTokenClaims.put("nonce", query.get("nonce"));
        String code = PROVIDER.issueCode(idTokenClaims);
        return browser.get(query.get("redirect_uri") + "?code=" + code + "&state="
                + java.net.URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8));
    }

    private static Map<String, Object> claims(String subject, String email, boolean verified, String givenName,
                                              String familyName, String locale) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("email_verified", verified);
        claims.put("name", givenName + " " + familyName);
        claims.put("given_name", givenName);
        claims.put("family_name", familyName);
        claims.put("locale", locale);
        claims.put("picture", "https://lh3.googleusercontent.test/a/" + subject);
        return claims;
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow(() -> new AssertionError(
                "No redirect, status " + response.statusCode() + ": " + response.body()));
    }

    private static Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0],
                        pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)));
    }

    private List<String> actions(UUID userId) {
        return historyEntries.findAll().stream()
                .filter(entry -> userId.equals(entry.getSubjectId()))
                .sorted(java.util.Comparator.comparing(HistoryEntry::getCreatedAt)
                        .thenComparing(HistoryEntry::getOccurredAt))
                .map(HistoryEntry::getAction)
                .toList();
    }

    private HistoryEntry entry(UUID userId, String action) {
        return historyEntries.findAll().stream()
                .filter(entry -> userId.equals(entry.getSubjectId()) && action.equals(entry.getAction()))
                .findFirst().orElseThrow();
    }
}
