package network.foodsharing.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.api.model.Language;
import org.junit.jupiter.api.Test;

/** A1/D8.1 (moved from e2e a1-signup "unverified Google email is rejected"): what Google must report. */
class GoogleOidcUserServiceTest {

    @Test
    void onlyAVerifiedAddressSignsIn() {
        assertThat(GoogleOidcUserService.hasVerifiedEmail("gina@example.org", true)).isTrue();
        assertThat(GoogleOidcUserService.hasVerifiedEmail("gina@example.org", false)).isFalse();
        assertThat(GoogleOidcUserService.hasVerifiedEmail("gina@example.org", null)).isFalse();
        assertThat(GoogleOidcUserService.hasVerifiedEmail(null, true)).isFalse();
        assertThat(GoogleOidcUserService.hasVerifiedEmail("  ", true)).isFalse();
    }

    @Test
    void googleLocalesBecomePlatformLanguages() {
        assertThat(GoogleOidcUserService.language("de-CH")).isEqualTo(Language.DE);
        assertThat(GoogleOidcUserService.language("gsw")).isEqualTo(Language.GWS);
        assertThat(GoogleOidcUserService.language("fr_CH")).isEqualTo(Language.FR);
        assertThat(GoogleOidcUserService.language("it")).isEqualTo(Language.IT);
        assertThat(GoogleOidcUserService.language("en-GB")).isEqualTo(Language.EN);
        assertThat(GoogleOidcUserService.language("es")).isNull();
        assertThat(GoogleOidcUserService.language(null)).isNull();
    }
}
