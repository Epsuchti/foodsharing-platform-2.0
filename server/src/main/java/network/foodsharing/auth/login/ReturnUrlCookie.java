package network.foodsharing.auth.login;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import network.foodsharing.auth.service.ReturnUrlValidator;
import network.foodsharing.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Carries the validated return URL through the Google round trip in a short-lived, HMAC-signed, HttpOnly cookie
 * {@code fs_oauth_return} ({@code base64url(url).base64url(hmacSha256(url))}). The value is validated again when read.
 */
@Component
public class ReturnUrlCookie {

    public static final String NAME = "fs_oauth_return";
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private final AppProperties properties;
    private final ReturnUrlValidator validator;

    public ReturnUrlCookie(AppProperties properties, ReturnUrlValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    public void write(HttpServletResponse response, String returnUrl) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(returnUrl.getBytes(StandardCharsets.UTF_8));
        response.addHeader(HttpHeaders.SET_COOKIE, base(encoded + "." + sign(returnUrl)).maxAge(MAX_AGE).build()
                .toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base("").maxAge(Duration.ZERO).build().toString());
    }

    /** The return URL when the cookie is present, correctly signed and still a safe in-app path. */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .flatMap(this::decode)
                .flatMap(validator::sanitize);
    }

    Optional<String> decode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        int dot = value.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        try {
            String url = new String(Base64.getUrlDecoder().decode(value.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] expected = sign(url).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = value.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, actual) ? Optional.of(url) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getAuth().getSigningSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((NAME + "|" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 not available", ex);
        }
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.getAuth().isCookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}
