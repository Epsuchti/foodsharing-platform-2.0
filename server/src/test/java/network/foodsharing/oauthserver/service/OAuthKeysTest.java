package network.foodsharing.oauthserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OAuthKeysTest {

    @Test
    void generatedKeyOnlyWhenAllowed() {
        assertThat(OAuthKeys.load("", true).getKeyId()).hasSize(16);
        assertThatThrownBy(() -> OAuthKeys.load("", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAUTH_SIGNING_KEY");
        assertThatThrownBy(() -> OAuthKeys.load(null, false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configuredKeyIsUsedEvenWhenGenerationIsForbidden() {
        var generated = OAuthKeys.load("", true);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generated.getPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        assertThat(OAuthKeys.load(pem, false).getKeyId()).isEqualTo(generated.getKeyId());
    }

    @Test
    void prodRequiresTheKeyWithoutDefault() throws IOException {
        Properties prod = new Properties();
        try (var in = new ClassPathResource("application-prod.properties").getInputStream()) {
            prod.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertThat(prod.getProperty("app.oauth.signing-key-pem")).isEqualTo("${OAUTH_SIGNING_KEY}");
        assertThat(prod.getProperty("app.oauth.allow-generated-key")).isEqualTo("false");
    }
}
