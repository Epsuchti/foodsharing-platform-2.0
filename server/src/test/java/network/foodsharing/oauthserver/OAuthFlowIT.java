package network.foodsharing.oauthserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/** C13 end to end: authorize → consent → token → userinfo → refresh, PKCE, region restriction, admin, connections. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oauth-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class OAuthFlowIT {

    private static final String CALLBACK = "http://localhost:8080/dev/oauth-callback";
    private static final String ZUERICH = "00000010-0001-4000-8000-000000000040";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String WIKI_BASIC = "Basic " + Base64.getEncoder()
            .encodeToString("wiki:wiki-local-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mvc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    private String authorize(Cookie user, String clientId, String scope, String challenge) throws Exception {
        var request = get("/oauth/authorize").cookie(user).param("response_type", "code").param("client_id", clientId)
                .param("redirect_uri", CALLBACK).param("scope", scope).param("state", "st-1").param("nonce", "n-1");
        if (challenge != null) {
            request.param("code_challenge", challenge).param("code_challenge_method", "S256");
        }
        return mvc.perform(request).andExpect(status().isFound()).andReturn().getResponse()
                .getHeader(HttpHeaders.LOCATION);
    }

    private static String query(String location, String name) {
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst(name);
    }

    @Test
    void rememberedConsentCodeTokenUserInfoRefresh() throws Exception {
        Cookie foodsaver = login(SeedUsers.FOODSAVER);
        String location = authorize(foodsaver, "wiki", "openid profile email regions", null);
        assertThat(location).startsWith(CALLBACK + "?code=").contains("state=st-1");
        String code = query(location, "code");

        String tokens = mvc.perform(post("/oauth/token").header(HttpHeaders.AUTHORIZATION, WIKI_BASIC)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "authorization_code")
                        .param("code", code).param("redirect_uri", CALLBACK))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600)).andExpect(jsonPath("$.id_token").isString())
                .andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(tokens, "$.access_token");
        String refresh = JsonPath.read(tokens, "$.refresh_token");

        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("client_id", "wiki")
                        .param("client_secret", "wrong").param("code", "x").param("redirect_uri", CALLBACK))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));

        mvc.perform(get("/oauth/userinfo").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sub").value(SeedUsers.FOODSAVER.id().toString()))
                .andExpect(jsonPath("$.email").value("foodsaver@local"))
                .andExpect(jsonPath("$.region_ids[?(@ == '" + ZUERICH + "')]").exists());
        mvc.perform(get("/oauth/userinfo").header(HttpHeaders.AUTHORIZATION, "Bearer nonsense"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_token"));

        String rotated = mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token").param("client_id", "wiki")
                        .param("client_secret", "wiki-local-secret").param("refresh_token", refresh))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(rotated, "$.refresh_token")).isNotEqualTo(refresh);
        mvc.perform(post("/oauth/token").header(HttpHeaders.AUTHORIZATION, WIKI_BASIC)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token")
                        .param("refresh_token", refresh))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        // reusing the old refresh token revoked the family, so the rotated one is dead too
        mvc.perform(post("/oauth/token").header(HttpHeaders.AUTHORIZATION, WIKI_BASIC)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token")
                        .param("refresh_token", (String) JsonPath.read(rotated, "$.refresh_token")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        // a replayed code is rejected
        mvc.perform(post("/oauth/token").header(HttpHeaders.AUTHORIZATION, WIKI_BASIC)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "authorization_code")
                        .param("code", code).param("redirect_uri", CALLBACK))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));

        mvc.perform(get("/api/users/me/connections").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("wiki"));
        mvc.perform(delete("/api/users/me/connections/wiki").with(csrf()).cookie(foodsaver))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me/connections").cookie(foodsaver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(authorize(foodsaver, "wiki", "openid", null)).startsWith("/oauth/consent?request=");
    }

    @Test
    void publicClientNeedsPkceAndConsent() throws Exception {
        Cookie jumper = login(SeedUsers.JUMPER);
        assertThat(authorize(jumper, "mobile-dev", "openid profile", null)).contains("error=invalid_request");
        assertThat(authorize(jumper, "mobile-dev", "openid regions", CHALLENGE)).contains("error=invalid_scope");
        String consentPage = authorize(jumper, "mobile-dev", "openid profile", CHALLENGE);
        assertThat(consentPage).startsWith("/oauth/consent?request=");
        String requestId = query(consentPage, "request");

        mvc.perform(get("/api/oauth/authorize-requests/" + requestId).cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.client.clientId").value("mobile-dev"))
                .andExpect(jsonPath("$.alreadyRemembered").value(false)).andExpect(jsonPath("$.regionAllowed").value(true));
        mvc.perform(get("/api/oauth/authorize-requests/" + requestId).cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
        String decided = mvc.perform(post("/api/oauth/authorize-requests/" + requestId + "/decision").with(csrf())
                        .cookie(jumper).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true,\"remember\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String code = query(JsonPath.read(decided, "$.redirectUri"), "code");
        mvc.perform(get("/api/oauth/authorize-requests/" + requestId).cookie(jumper))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OAUTH_REQUEST_EXPIRED"));

        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("client_id", "mobile-dev").param("code", code)
                        .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER.replace('d', 'e')))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("client_id", "mobile-dev").param("code", code)
                        .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isString());
        mvc.perform(get("/api/dev/oauth/last-code").cookie(jumper)).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("mobile-dev"));
        // remembered now: straight back with a code
        assertThat(authorize(jumper, "mobile-dev", "openid profile", CHALLENGE)).startsWith(CALLBACK + "?code=");
    }

    @Test
    void regionRestrictionDeclineAndErrors() throws Exception {
        Cookie newbie = login(SeedUsers.NEWBIE);
        String consentPage = authorize(newbie, "wiki", "openid", null);
        String requestId = query(consentPage, "request");
        mvc.perform(get("/api/oauth/authorize-requests/" + requestId).cookie(newbie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.regionAllowed").value(false));
        mvc.perform(post("/api/oauth/authorize-requests/" + requestId + "/decision").with(csrf()).cookie(newbie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"remember\":false}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OAUTH_REGION_REQUIRED"));
        String declined = mvc.perform(post("/api/oauth/authorize-requests/" + requestId + "/decision").with(csrf())
                        .cookie(newbie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":false,\"remember\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(declined, "$.redirectUri")).contains("error=access_denied")
                .contains("state=st-1");

        mvc.perform(get("/oauth/authorize").param("client_id", "wiki").param("response_type", "code"))
                .andExpect(status().isFound()).andExpect(result -> assertThat(result.getResponse()
                        .getHeader(HttpHeaders.LOCATION)).startsWith("/login?returnUrl="));
        mvc.perform(get("/oauth/authorize").cookie(newbie).param("client_id", "wiki").param("response_type", "code")
                        .param("redirect_uri", "https://evil.example.org/cb").param("scope", "openid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));

        mvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").isString())
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA")).andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void clientAdministrationIsOrgaOnly() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        String body = "{\"clientId\":\"partner-app\",\"name\":\"Partner\",\"confidential\":true,"
                + "\"redirectUris\":[\"https://partner.example.org/cb\"],\"scopes\":[\"openid\",\"email\"],"
                + "\"grantTypes\":[\"authorization_code\"],\"requiredRegionIds\":[],\"enabled\":true}";
        mvc.perform(post("/api/admin/oauth-clients").with(csrf()).cookie(login(SeedUsers.FOODSAVER))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/oauth-clients").with(csrf()).cookie(orga).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSecret").isString()).andExpect(jsonPath("$.client.consentCount").value(0));
        mvc.perform(post("/api/admin/oauth-clients").with(csrf()).cookie(orga).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/oauth-clients").with(csrf()).cookie(orga).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("partner-app", "partner-2").replace("https://partner", "http://partner")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OAUTH_REDIRECT_URI_INVALID"));
        mvc.perform(post("/api/admin/oauth-clients/mobile-dev/secret").with(csrf()).cookie(orga))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OAUTH_CLIENT_PUBLIC"));
        mvc.perform(get("/api/admin/oauth-clients").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clientId == 'wiki')].consentCount").exists());
        mvc.perform(delete("/api/admin/oauth-clients/partner-app").with(csrf()).cookie(orga))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/oauth-clients/partner-app").cookie(orga)).andExpect(status().isNotFound());
    }
}
