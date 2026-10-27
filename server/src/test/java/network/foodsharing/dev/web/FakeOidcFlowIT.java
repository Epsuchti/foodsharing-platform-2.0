package network.foodsharing.dev.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The fake OIDC provider's endpoints over real HTTP (e2e profile): discovery, the account chooser page, authorize to
 * code, code to tokens (signature checked against the JWKS), userinfo, single-use codes and client authentication.
 * The full Spring {@code oauth2Login} round trip (session cookie, account creation) is covered by T4's GoogleLoginIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:fake-oidc-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@ActiveProfiles("e2e")
class FakeOidcFlowIT {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    @LocalServerPort
    private int port;

    private RestClient http;
    private String issuer;
    private String redirectUri;

    @BeforeEach
    void setUp() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(client))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        issuer = "http://localhost:" + port + "/dev/oidc";
        redirectUri = "http://localhost:" + port + "/login/oauth2/code/google";
    }

    @Test
    void discoveryDescribesTheProvider() {
        Map<String, Object> metadata = http.get().uri("/dev/oidc/.well-known/openid-configuration").retrieve().body(JSON);
        assertThat(metadata).containsEntry("issuer", issuer)
                .containsEntry("authorization_endpoint", issuer + "/authorize")
                .containsEntry("token_endpoint", issuer + "/token")
                .containsEntry("jwks_uri", issuer + "/jwks")
                .containsEntry("userinfo_endpoint", issuer + "/userinfo");
    }

    @Test
    void authorizePageListsSeedGoogleUsersAndTheNewUserForm() {
        String html = http.get().uri(uriBuilder -> uriBuilder.path("/dev/oidc/authorize")
                        .queryParam("response_type", "code").queryParam("client_id", "dev-client")
                        .queryParam("redirect_uri", redirectUri).queryParam("scope", "openid email profile")
                        .queryParam("state", "st-1").queryParam("nonce", "n-1").build())
                .retrieve().body(String.class);
        assertThat(html).contains("data-testid=\"dev.oidc.account-foodsaver\"", "data-testid=\"dev.oidc.account-bot\"",
                "foodsaver@local", "Sarah Müller", "data-testid=\"dev.oidc.new-email\"",
                "data-testid=\"dev.oidc.new-unverified\"", "data-testid=\"dev.oidc.cancel\"");
        assertThat(html).doesNotContain("newbie@local");
    }

    @Test
    void authorizeRejectsForeignRedirectUris() {
        ResponseEntity<String> page = http.get().uri(uriBuilder -> uriBuilder.path("/dev/oidc/authorize")
                        .queryParam("response_type", "code").queryParam("client_id", "dev-client")
                        .queryParam("redirect_uri", "https://evil.example/cb").build())
                .retrieve().toEntity(String.class);
        assertThat(page.getBody()).contains("data-testid=\"dev.oidc.error\"").doesNotContain("dev.oidc.account-");
    }

    @Test
    void seedAccountCodeExchangesForASignedIdTokenAndUserinfo() throws Exception {
        MultiValueMap<String, String> form = authorizeForm("st-42", "nonce-42");
        form.add("choice", "account");
        form.add("account", "fake-google-foodsaver");
        String code = authorizeAndGetCode(form, "st-42");

        ResponseEntity<Map<String, Object>> tokenResponse = token(code, "dev-client", "dev-secret");
        assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> tokens = tokenResponse.getBody();
        assertThat(tokens).containsEntry("token_type", "Bearer").containsKeys("access_token", "id_token", "expires_in");

        SignedJWT idToken = SignedJWT.parse((String) tokens.get("id_token"));
        String jwks = http.get().uri("/dev/oidc/jwks").retrieve().body(String.class);
        RSAKey key = JWKSet.parse(jwks).getKeys().getFirst().toRSAKey();
        assertThat(key.isPrivate()).isFalse();
        assertThat(idToken.getHeader().getKeyID()).isEqualTo(key.getKeyID());
        assertThat(idToken.verify(new RSASSAVerifier(key))).isTrue();

        JWTClaimsSet claims = idToken.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(issuer);
        assertThat(claims.getAudience()).containsExactly("dev-client");
        assertThat(claims.getSubject()).isEqualTo("fake-google-foodsaver");
        assertThat(claims.getStringClaim("nonce")).isEqualTo("nonce-42");
        assertThat(claims.getStringClaim("email")).isEqualTo(SeedUsers.FOODSAVER.email());
        assertThat(claims.getBooleanClaim("email_verified")).isTrue();
        assertThat(claims.getStringClaim("given_name")).isEqualTo("Sarah");
        assertThat(claims.getStringClaim("family_name")).isEqualTo("Müller");
        assertThat(claims.getStringClaim("locale")).isEqualTo("de");
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());

        Map<String, Object> userInfo = http.get().uri("/dev/oidc/userinfo")
                .header("Authorization", "Bearer " + tokens.get("access_token")).retrieve().body(JSON);
        assertThat(userInfo).containsEntry("sub", "fake-google-foodsaver").containsEntry("email", "foodsaver@local");

        // codes are single use
        ResponseEntity<Map<String, Object>> reused = token(code, "dev-client", "dev-secret");
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reused.getBody()).containsEntry("error", "invalid_grant");
    }

    @Test
    void newUnverifiedGoogleUserGetsAStableSubjectAndEmailVerifiedFalse() throws Exception {
        String subject = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            MultiValueMap<String, String> form = authorizeForm("s", "n");
            form.add("choice", "new");
            form.add("email", "Anna.Neu@Example.ch");
            form.add("name", "Anna Neu");
            form.add("locale", "fr");
            form.add("unverified", "on");
            String code = authorizeAndGetCode(form, "s");
            JWTClaimsSet claims = SignedJWT.parse((String) token(code, "dev-client", "dev-secret").getBody()
                    .get("id_token")).getJWTClaimsSet();
            assertThat(claims.getStringClaim("email")).isEqualTo("anna.neu@example.ch");
            assertThat(claims.getBooleanClaim("email_verified")).isFalse();
            assertThat(claims.getStringClaim("given_name")).isEqualTo("Anna");
            assertThat(claims.getStringClaim("locale")).isEqualTo("fr");
            if (subject != null) {
                assertThat(claims.getSubject()).isEqualTo(subject);
            }
            subject = claims.getSubject();
        }
        assertThat(subject).startsWith("fake-google-new-");
    }

    @Test
    void cancelRedirectsWithAccessDenied() {
        MultiValueMap<String, String> form = authorizeForm("st-c", null);
        form.add("choice", "cancel");
        ResponseEntity<Void> response = http.post().uri("/dev/oidc/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString())
                .startsWith(redirectUri).contains("error=access_denied").contains("state=st-c");
    }

    @Test
    void tokenEndpointRejectsWrongClientSecretAndUnknownAccessTokens() {
        MultiValueMap<String, String> form = authorizeForm("s", "n");
        form.add("choice", "account");
        form.add("account", "fake-google-bot");
        String code = authorizeAndGetCode(form, "s");
        ResponseEntity<Map<String, Object>> wrong = token(code, "dev-client", "nope");
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).containsEntry("error", "invalid_client");

        ResponseEntity<String> userInfo = http.get().uri("/dev/oidc/userinfo")
                .header("Authorization", "Bearer unknown").retrieve().toEntity(String.class);
        assertThat(userInfo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private MultiValueMap<String, String> authorizeForm(String state, String nonce) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "dev-client");
        form.add("redirect_uri", redirectUri);
        form.add("scope", "openid email profile");
        form.add("state", state);
        if (nonce != null) {
            form.add("nonce", nonce);
        }
        return form;
    }

    private String authorizeAndGetCode(MultiValueMap<String, String> form, String expectedState) {
        ResponseEntity<Void> response = http.post().uri("/dev/oidc/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        URI location = response.getHeaders().getLocation();
        assertThat(location.toString()).startsWith(redirectUri);
        var query = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        assertThat(query.getFirst("state")).isEqualTo(expectedState);
        return query.getFirst("code");
    }

    private ResponseEntity<Map<String, Object>> token(String code, String clientId, String secret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        return http.post().uri("/dev/oidc/token")
                .headers(headers -> headers.setBasicAuth(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toEntity(JSON);
    }
}
