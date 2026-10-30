package network.foodsharing.oauthserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.oauthserver.service.OAuthClientService;
import network.foodsharing.oauthserver.service.OAuthCrypto;
import network.foodsharing.oauthserver.service.UserInfoService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** A44: PKCE S256, hashing, redirect URI registration and the userinfo scope matrix. */
class OAuthRulesTest {

    @Test
    void pkceS256MatchesRfc7636Example() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertThat(OAuthCrypto.verifyS256(verifier, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")).isTrue();
        assertThat(OAuthCrypto.verifyS256(verifier, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cX")).isFalse();
        assertThat(OAuthCrypto.verifyS256("short", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")).isFalse();
        assertThat(OAuthCrypto.verifyS256(null, "x")).isFalse();
    }

    @Test
    void tokensAndSecretsAreRandomAndHashed() {
        assertThat(OAuthCrypto.randomToken(32)).isNotEqualTo(OAuthCrypto.randomToken(32));
        assertThat(Base64.getUrlDecoder().decode(OAuthCrypto.randomToken(32))).hasSize(32);
        assertThat(OAuthCrypto.randomSecret(48)).hasSize(48);
        assertThat(OAuthCrypto.sha256Hex("wiki-local-secret"))
                .isEqualTo("b5d6010658224b136654a145064e91c9497cec8141815232671abcd2c4deb3e2");
        assertThat(OAuthCrypto.sameHash("ab", "ab")).isTrue();
        assertThat(OAuthCrypto.sameHash("ab", null)).isFalse();
    }

    @Test
    void redirectUrisMustBeHttpsOrLocalhost() {
        assertThat(OAuthClientService.validRedirectUri("https://wiki.example.org/callback")).isTrue();
        assertThat(OAuthClientService.validRedirectUri("http://localhost:8080/cb")).isTrue();
        assertThat(OAuthClientService.validRedirectUri("http://127.0.0.1/cb")).isTrue();
        assertThat(OAuthClientService.validRedirectUri("http://wiki.example.org/cb")).isFalse();
        assertThat(OAuthClientService.validRedirectUri("https://wiki.example.org/cb#x")).isFalse();
        assertThat(OAuthClientService.validRedirectUri("/relative")).isFalse();
        assertThat(OAuthClientService.validRedirectUri("javascript:alert(1)")).isFalse();
    }

    record Examples(List<String> valid, List<String> invalid) {
    }

    record ClientCases(String about, Examples redirectUris, Examples clientIds) {
    }

    /** The cases the client's {@code redirectUriProblem}/{@code validClientId} run too (decision 116). */
    @Test
    void registrationRulesMatchTheSharedCases() throws Exception {
        ClientCases fixture;
        try (InputStream in = getClass().getResourceAsStream("/rule-cases/oauth-client.json")) {
            fixture = JsonMapper.builder().build().readValue(in, ClientCases.class);
        }
        assertThat(fixture.redirectUris().valid()).isNotEmpty()
                .allSatisfy(uri -> assertThat(OAuthClientService.registrableRedirectUri(uri)).as(uri).isTrue());
        assertThat(fixture.redirectUris().invalid()).isNotEmpty()
                .allSatisfy(uri -> assertThat(OAuthClientService.registrableRedirectUri(uri)).as(uri).isFalse());
        assertThat(fixture.clientIds().valid()).isNotEmpty()
                .allSatisfy(id -> assertThat(OAuthClientService.validClientId(id)).as(id).isTrue());
        assertThat(fixture.clientIds().invalid()).isNotEmpty()
                .allSatisfy(id -> assertThat(OAuthClientService.validClientId(id)).as(id).isFalse());
        assertThat(OAuthClientService.registrableRedirectUri(null)).isFalse();
        assertThat(OAuthClientService.validClientId(null)).isFalse();
    }

    @Test
    void clientKindChangesOnlyFromConfidentialToPublic() {
        assertThat(OAuthClientService.mayChangeKind(false, true)).isFalse();
        assertThat(OAuthClientService.mayChangeKind(false, false)).isTrue();
        assertThat(OAuthClientService.mayChangeKind(false, null)).isTrue();
        assertThat(OAuthClientService.mayChangeKind(true, false)).isTrue();
        assertThat(OAuthClientService.mayChangeKind(true, true)).isTrue();
        assertThat(OAuthClientService.mayChangeKind(true, null)).isTrue();
    }

    @Test
    void requiredRegionsKeepTheStoredOrderAndSkipDeletedOnes() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        Region regionA = new Region();
        regionA.setId(a);
        Region regionB = new Region();
        regionB.setId(b);
        assertThat(OAuthClientService.inOrder(java.util.List.of(b, gone, a, b), java.util.List.of(regionA, regionB)))
                .containsExactly(regionB, regionA);
        assertThat(OAuthClientService.inOrder(java.util.List.of(), java.util.List.of())).isEmpty();
    }

    @Test
    void userInfoClaimsPerScope() {
        User user = new User();
        user.setId(UUID.fromString("00000001-0001-4000-8000-000000000006"));
        user.setDisplayName("Sarah Müller");
        user.setFirstName("Sarah");
        user.setLastName("Müller");
        user.setFoodsaverNumber(100006L);
        user.setEmail("foodsaver@local");
        user.setAvatarUrl("/images/avatars/foodsaver.svg");
        user.setPreferredLanguage(Language.FR);
        UUID region = UUID.randomUUID();

        Map<String, Object> minimal = UserInfoService.claims(user, Set.of("openid"), Set.of(region), Set.of(),
                "https://example.org/");
        assertThat(minimal).containsOnlyKeys("sub", "preferred_username")
                .containsEntry("preferred_username", "Sarah Müller#100006");

        Map<String, Object> full = UserInfoService.claims(user, Set.of("openid", "profile", "email", "regions"),
                Set.of(region), Set.of(region), "https://example.org/");
        assertThat(full).containsEntry("name", "Sarah Müller").containsEntry("given_name", "Sarah")
                .containsEntry("family_name", "Müller").containsEntry("locale", "fr-CH")
                .containsEntry("picture", "https://example.org/images/avatars/foodsaver.svg")
                .containsEntry("email", "foodsaver@local").containsEntry("email_verified", true)
                .containsEntry("region_ids", java.util.List.of(region.toString()))
                .containsEntry("region_ambassador", java.util.List.of(region.toString()));
        assertThat(UserInfoService.claims(user, Set.of("openid", "email"), Set.of(), Set.of(), "https://x"))
                .containsKeys("email").doesNotContainKeys("name", "region_ids");
    }
}
