package network.foodsharing.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import org.springframework.stereotype.Component;

/**
 * Open-redirect guard for return URLs (docs/conventions.md A9, reference {@code AuthService.safeReturnUrl}): only
 * in-app absolute paths ({@code /…}, no {@code //}, no {@code \}, at most 500 characters, URL-safe characters
 * only) that are not login, API or framework routes. The frontend's {@code safeReturnUrl()} applies the same rules.
 */
@Component
public class ReturnUrlValidator {

    public static final int MAX_LENGTH = 500;

    private static final Pattern ALLOWED = Pattern.compile("/[A-Za-z0-9/_\\-.~?=&%,:+@;$!*'()#]*");
    /** Path prefixes (exact or followed by {@code /}) that are never a return target. */
    private static final List<String> FORBIDDEN_PREFIXES = List.of("/login", "/logout", "/api", "/oauth2",
            "/webauthn", "/dev/oidc", "/actuator", "/h2-console");

    /** The trimmed URL when it is a safe in-app path, otherwise empty (also for null/blank). */
    public Optional<String> sanitize(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return Optional.empty();
        }
        String url = returnUrl.strip();
        if (url.length() > MAX_LENGTH || url.startsWith("//") || url.contains("\\") || url.contains("/./")
                || url.contains("/../") || !ALLOWED.matcher(url).matches()) {
            return Optional.empty();
        }
        String path = url.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return Optional.empty();
            }
        }
        return Optional.of(url);
    }

    /**
     * For explicit user input: null/blank → null, a safe URL → the trimmed URL.
     *
     * @throws ApiException 400 INVALID_RETURN_URL for anything else
     */
    public String require(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return null;
        }
        return sanitize(returnUrl).orElseThrow(() -> ApiException.badRequest(ApiErrorCode.INVALID_RETURN_URL));
    }
}
