package network.foodsharing.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import network.foodsharing.config.AppProperties;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionCookieAuthenticationFilterTest {

    private final SessionAuthenticator authenticator = mock(SessionAuthenticator.class);
    private final AppProperties properties = new AppProperties();
    private final SessionCookieAuthenticationFilter filter =
            new SessionCookieAuthenticationFilter(authenticator, new SessionCookieWriter(properties));
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<Authentication> seenByChain = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void requestsWithoutCookieStayAnonymous() throws Exception {
        run(new MockHttpServletRequest());

        verify(authenticator, never()).authenticate(anyString());
        assertThat(seenByChain.get()).isNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void aValidCookieAuthenticatesTheRequestWithRoleAuthorities() throws Exception {
        SessionPrincipal principal = TestAuth.principal(SeedUsers.BIEB);
        when(authenticator.authenticate("raw-token")).thenReturn(Optional.of(principal));

        run(withCookie("fs_session", "raw-token"));

        assertThat(seenByChain.get()).isInstanceOf(SessionAuthenticationToken.class);
        assertThat(seenByChain.get().getPrincipal()).isEqualTo(principal);
        assertThat(seenByChain.get().isAuthenticated()).isTrue();
        assertThat(seenByChain.get().getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_FOODSHARER", "ROLE_FOODSAVER", "ROLE_STORE_MANAGER");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void anInvalidCookieIsClearedAndTheRequestContinuesAnonymously() throws Exception {
        when(authenticator.authenticate("stale")).thenReturn(Optional.empty());

        run(withCookie("fs_session", "stale"));

        assertThat(seenByChain.get()).isNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("fs_session=;").contains("Max-Age=0", "HttpOnly", "Path=/");
    }

    @Test
    void otherAndBlankCookiesAreIgnored() throws Exception {
        run(withCookie("JSESSIONID", "abc"));
        run(withCookie("fs_session", " "));

        verify(authenticator, never()).authenticate(anyString());
        assertThat(seenByChain.get()).isNull();
    }

    @Test
    void theConfiguredCookieNameIsUsed() throws Exception {
        properties.getAuth().setCookieName("custom");
        when(authenticator.authenticate("t")).thenReturn(Optional.of(TestAuth.principal(SeedUsers.ORGA)));

        run(withCookie("fs_session", "ignored"));
        assertThat(seenByChain.get()).isNull();
        run(withCookie("custom", "t"));
        assertThat(seenByChain.get().getPrincipal()).isEqualTo(TestAuth.principal(SeedUsers.ORGA));
    }

    @Test
    void anAlreadyAuthenticatedRequestIsNotLookedUpAgain() throws Exception {
        TestAuth.login(TestAuth.principal(SeedUsers.FOODSAVER));

        run(withCookie("fs_session", "raw-token"));

        verify(authenticator, never()).authenticate(anyString());
        assertThat(seenByChain.get().getPrincipal()).isEqualTo(TestAuth.principal(SeedUsers.FOODSAVER));
    }

    @Test
    void theCookieIsSecureWhenConfigured() throws Exception {
        properties.getAuth().setCookieSecure(true);
        when(authenticator.authenticate("stale")).thenReturn(Optional.empty());

        run(withCookie("fs_session", "stale"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure", "SameSite=Lax");
    }

    private void run(MockHttpServletRequest request) throws Exception {
        seenByChain.set(null);
        filter.doFilter(request, response,
                (req, res) -> seenByChain.set(SecurityContextHolder.getContext().getAuthentication()));
    }

    private static MockHttpServletRequest withCookie(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setCookies(new Cookie(name, value));
        return request;
    }
}
