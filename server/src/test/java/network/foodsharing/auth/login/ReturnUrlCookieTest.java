package network.foodsharing.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.service.ReturnUrlValidator;
import network.foodsharing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReturnUrlCookieTest {

    private final AppProperties properties = new AppProperties();
    private final ReturnUrlCookie cookie = new ReturnUrlCookie(properties, new ReturnUrlValidator());

    @Test
    void roundTripsASignedHttpOnlyCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookie.write(response, "/account/security?tab=passkeys");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("fs_oauth_return=").contains("HttpOnly").contains("SameSite=Lax")
                .contains("Max-Age=600");
        assertThat(cookie.read(requestWith(value(header)))).contains("/account/security?tab=passkeys");
    }

    @Test
    void rejectsTamperedForeignOrUnsafeValues() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookie.write(response, "/dashboard");
        String value = value(response.getHeader("Set-Cookie"));

        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("/stores".getBytes())
                + value.substring(value.indexOf('.'));
        assertThat(cookie.read(requestWith(forged))).isEmpty();
        assertThat(cookie.read(requestWith("garbage"))).isEmpty();
        assertThat(cookie.read(new MockHttpServletRequest())).isEmpty();

        AppProperties otherSecret = new AppProperties();
        otherSecret.getAuth().setSigningSecret("another-secret");
        assertThat(new ReturnUrlCookie(otherSecret, new ReturnUrlValidator()).read(requestWith(value))).isEmpty();

        MockHttpServletResponse unsafe = new MockHttpServletResponse();
        cookie.write(unsafe, "//evil.example");
        assertThat(cookie.read(requestWith(value(unsafe.getHeader("Set-Cookie"))))).isEmpty();
    }

    @Test
    void mapsGoogleLocalesToLanguages() {
        assertThat(GoogleOidcUserService.language("de-CH")).isEqualTo(Language.DE);
        assertThat(GoogleOidcUserService.language("gsw")).isEqualTo(Language.GWS);
        assertThat(GoogleOidcUserService.language("fr_CH")).isEqualTo(Language.FR);
        assertThat(GoogleOidcUserService.language("it")).isEqualTo(Language.IT);
        assertThat(GoogleOidcUserService.language("en-GB")).isEqualTo(Language.EN);
        assertThat(GoogleOidcUserService.language("pt-BR")).isNull();
        assertThat(GoogleOidcUserService.language(null)).isNull();
    }

    @Test
    void failureRedirectCarriesTheCodeAndReturnUrl() {
        assertThat(GoogleLoginFailureHandler.errorUrl("GOOGLE_LOGIN_FAILED", null))
                .isEqualTo("/login?error=GOOGLE_LOGIN_FAILED");
        assertThat(GoogleLoginFailureHandler.errorUrl("IP_BLOCKED", "/a?b=c"))
                .isEqualTo("/login?error=IP_BLOCKED&returnUrl=%2Fa%3Fb%3Dc");
    }

    private static String value(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private static MockHttpServletRequest requestWith(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ReturnUrlCookie.NAME, value));
        return request;
    }
}
