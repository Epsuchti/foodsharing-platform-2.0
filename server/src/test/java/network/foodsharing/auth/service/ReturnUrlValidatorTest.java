package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReturnUrlValidatorTest {

    private final ReturnUrlValidator validator = new ReturnUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {"/", "/dashboard", "/account/security?tab=sessions", "/stores/42#team",
            "/history?from=2026-01-01T00:00:00Z&size=20", "/loginhelp", "/search?q=caf%C3%A9"})
    void acceptsInAppPaths(String url) {
        assertThat(validator.sanitize(url)).contains(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/", "//evil.example", "/\\evil.example", "evil", "javascript:alert(1)",
            "/login", "/login?returnUrl=/x", "/login/verify?token=a", "/LOGIN", "/logout", "/api/auth/session",
            "/oauth2/authorization/google", "/webauthn/register", "/dev/oidc/authorize", "/actuator/health",
            "/a/../login", "/a b", "/a\nb", "/<script>"})
    void rejectsEverythingElse(String url) {
        assertThat(validator.sanitize(url)).isEmpty();
    }

    @Test
    void rejectsTooLongUrlsAndTrims() {
        assertThat(validator.sanitize("/" + "a".repeat(500))).isEmpty();
        assertThat(validator.sanitize("/" + "a".repeat(499))).isPresent();
        assertThat(validator.sanitize("  /dashboard  ")).contains("/dashboard");
    }

    @Test
    void requireReturnsNullForBlankAndThrowsForUnsafe() {
        assertThat(validator.require(null)).isNull();
        assertThat(validator.require(" ")).isNull();
        assertThat(validator.require("/account")).isEqualTo("/account");
        assertThatThrownBy(() -> validator.require("https://evil.example"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ApiErrorCode.INVALID_RETURN_URL);
                    assertThat(ex.getStatus().value()).isEqualTo(400);
                });
    }
}
