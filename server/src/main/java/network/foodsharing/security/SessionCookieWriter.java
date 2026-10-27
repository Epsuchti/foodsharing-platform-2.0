package network.foodsharing.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import network.foodsharing.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Reads and writes the {@code fs_session} cookie (HttpOnly, SameSite=Lax, Secure in prod, path /). */
@Component
public class SessionCookieWriter {

    private final AppProperties properties;

    public SessionCookieWriter(AppProperties properties) {
        this.properties = properties;
    }

    public String cookieName() {
        return properties.getAuth().getCookieName();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public void write(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, base(rawToken)
                .maxAge(Duration.ofDays(properties.getAuth().getSessionValidityDays()))
                .build().toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base("").maxAge(Duration.ZERO).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(cookieName(), value)
                .httpOnly(true)
                .secure(properties.getAuth().isCookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}
