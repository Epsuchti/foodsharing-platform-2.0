package network.foodsharing.dev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.dev.service.FakeOidcProvider.Account;
import network.foodsharing.dev.service.FakeOidcProvider.OidcError;
import network.foodsharing.testsupport.FixedClockConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The fake Google of the local/e2e profiles (was FakeOidcFlowIT): discovery, seed accounts, stable subjects for new
 * users, single-use codes bound to client and redirect URI, RS256 id tokens verifiable with the published JWKS,
 * userinfo and the OAuth errors. The browser round trip runs in journey account/sign-up-and-log-in.
 */
class FakeOidcProviderTest {

    private static final String ISSUER = "http://localhost:8081/dev/oidc";
    private static final String REDIRECT = "http://localhost:8081/login/oauth2/code/google";

    private final UserService users = mock(UserService.class);
    private final MovableClock real = new MovableClock(Instant.parse("2026-11-16T12:00:00Z"));
    private FakeOidcProvider provider;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("http://localhost:8081");
        when(users.findActive(any(UUID.class))).thenReturn(Optional.empty());
        provider = new FakeOidcProvider(new DevUserCatalog(), users,
                new MutableClock(real, FixedClockConfig.SEED_NOW), properties, "dev-client", "dev-secret");
    }

    @Test
    void discoveryDescribesTheProvider() {
        assertThat(provider.discovery(ISSUER)).containsEntry("issuer", ISSUER)
                .containsEntry("authorization_endpoint", ISSUER + "/authorize")
                .containsEntry("token_endpoint", ISSUER + "/token")
                .containsEntry("jwks_uri", ISSUER + "/jwks")
                .containsEntry("userinfo_endpoint", ISSUER + "/userinfo");
    }

    @Test
    void onlySeedUsersWithAGoogleIdentityAreOffered() {
        when(users.findActive(SeedUsers.FOODSAVER.id())).thenReturn(Optional.of(user("foodsaver@local", "Sarah",
                "Müller", Language.DE)));
        when(users.findActive(SeedUsers.BOT.id())).thenReturn(Optional.of(user("bot@local", "Reto", "Baumann",
                Language.GWS)));
        when(users.findActive(SeedUsers.NEWBIE.id())).thenReturn(Optional.of(user("newbie@local", "Lea", "Brunner",
                Language.DE)));

        assertThat(provider.seedAccounts()).extracting(Account::email).containsExactly("foodsaver@local", "bot@local");
        Account bot = provider.seedAccount("fake-google-bot").orElseThrow();
        assertThat(bot.emailVerified()).isTrue();
        assertThat(bot.locale()).isEqualTo("de-CH");
        assertThat(bot.picture()).isEqualTo("http://localhost:8081/avatar.png");
        assertThat(provider.seedAccount("fake-google-newbie")).isEmpty();
    }

    @Test
    void aCodeExchangesOnceForASignedIdTokenAndUserinfo() throws Exception {
        Account account = provider.newAccount("sarah@example.org", "Sarah Müller", "de", true);
        String code = provider.issueCode("dev-client", REDIRECT, "nonce-42", "openid email profile", account);

        Map<String, Object> tokens = provider.exchange("authorization_code", code, REDIRECT, "dev-client",
                "dev-secret", ISSUER);
        assertThat(tokens).containsEntry("token_type", "Bearer").containsKeys("access_token", "id_token", "expires_in");

        SignedJWT idToken = SignedJWT.parse((String) tokens.get("id_token"));
        RSAKey key = JWKSet.parse(provider.jwks()).getKeys().getFirst().toRSAKey();
        assertThat(key.isPrivate()).isFalse();
        assertThat(idToken.getHeader().getKeyID()).isEqualTo(key.getKeyID());
        assertThat(idToken.verify(new RSASSAVerifier(key))).isTrue();
        JWTClaimsSet claims = idToken.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly("dev-client");
        assertThat(claims.getSubject()).isEqualTo(account.subject());
        assertThat(claims.getStringClaim("nonce")).isEqualTo("nonce-42");
        assertThat(claims.getBooleanClaim("email_verified")).isTrue();
        assertThat(claims.getStringClaim("given_name")).isEqualTo("Sarah");
        assertThat(claims.getStringClaim("family_name")).isEqualTo("Müller");
        assertThat(claims.getIssueTime().toInstant()).as("real time, not the fixed app clock").isEqualTo(real.instant());
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());

        assertThat(provider.userInfo((String) tokens.get("access_token"))).containsEntry("sub", account.subject())
                .containsEntry("email", "sarah@example.org");

        assertThatThrownBy(() -> provider.exchange("authorization_code", code, REDIRECT, "dev-client", "dev-secret",
                ISSUER)).isInstanceOfSatisfying(OidcError.class, error -> assertThat(error.error())
                .isEqualTo("invalid_grant"));
    }

    @Test
    void newUsersGetAStableSubjectFromTheirNormalisedEmail() {
        Account first = provider.newAccount(" Anna.Neu@Example.ch ", "Anna Neu", "fr", false);
        Account again = provider.newAccount("anna.neu@example.ch", null, null, false);
        assertThat(first.email()).isEqualTo("anna.neu@example.ch");
        assertThat(first.emailVerified()).isFalse();
        assertThat(first.givenName()).isEqualTo("Anna");
        assertThat(first.locale()).isEqualTo("fr");
        assertThat(first.subject()).startsWith("fake-google-new-").isEqualTo(again.subject());
        assertThat(again.name()).isEqualTo("anna.neu");
        assertThat(again.locale()).isEqualTo("de");
    }

    @Test
    void theTokenEndpointChecksClientGrantCodeAndRedirect() {
        Account account = provider.newAccount("a@example.org", "A", "de", true);
        String code = provider.issueCode("dev-client", REDIRECT, null, null, account);
        assertError(() -> provider.exchange("authorization_code", code, REDIRECT, "dev-client", "nope", ISSUER),
                401, "invalid_client");
        assertError(() -> provider.exchange("password", code, REDIRECT, "dev-client", "dev-secret", ISSUER),
                400, "unsupported_grant_type");
        assertError(() -> provider.exchange("authorization_code", code, "http://localhost:4200/cb", "dev-client",
                "dev-secret", ISSUER), 400, "invalid_grant");
        assertError(() -> provider.exchange("authorization_code", "unknown", REDIRECT, "dev-client", "dev-secret",
                ISSUER), 400, "invalid_grant");

        String late = provider.issueCode("dev-client", REDIRECT, null, null, account);
        real.advance(FakeOidcProvider.CODE_TTL.plusSeconds(1));
        assertError(() -> provider.exchange("authorization_code", late, REDIRECT, "dev-client", "dev-secret", ISSUER),
                400, "invalid_grant");
    }

    @Test
    void unknownOrExpiredAccessTokensAreRejected() {
        assertError(() -> provider.userInfo("unknown"), 401, "invalid_token");
        assertError(() -> provider.userInfo(null), 401, "invalid_token");
        Account account = provider.newAccount("a@example.org", "A", "de", true);
        String code = provider.issueCode("dev-client", REDIRECT, null, null, account);
        String token = (String) provider.exchange("authorization_code", code, REDIRECT, "dev-client", "dev-secret",
                ISSUER).get("access_token");
        real.advance(FakeOidcProvider.TOKEN_TTL.plusSeconds(1));
        assertError(() -> provider.userInfo(token), 401, "invalid_token");
    }

    @Test
    void googleReportsSwissGermanAsDeCh() {
        assertThat(FakeOidcProvider.googleLocale(Language.GWS)).isEqualTo("de-CH");
        assertThat(FakeOidcProvider.googleLocale(Language.FR)).isEqualTo("fr");
        assertThat(FakeOidcProvider.googleLocale(null)).isEqualTo("de");
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int status,
                                    String error) {
        assertThatThrownBy(call).isInstanceOfSatisfying(OidcError.class, ex -> {
            assertThat(ex.status().value()).isEqualTo(status);
            assertThat(ex.error()).isEqualTo(error);
            assertThat(ex.body()).containsEntry("error", error);
        });
    }

    private static User user(String email, String first, String last, Language language) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFirstName(first);
        user.setLastName(last);
        user.setDisplayName(first + " " + last);
        user.setPreferredLanguage(language);
        user.setAvatarUrl("/avatar.png");
        return user;
    }

    /** The "real" clock under the fixed app clock; the provider's TTLs run on it. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
