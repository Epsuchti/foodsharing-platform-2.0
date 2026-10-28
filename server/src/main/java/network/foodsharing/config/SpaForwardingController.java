package network.foodsharing.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forwards every non-API, non-file path to {@code index.html} so Angular deep links work. Paths owned by the backend
 * (API, actuator, Spring Security's OAuth2/WebAuthn endpoints, the fake OIDC provider, H2 console) are never
 * forwarded. {@code /login} itself is an Angular route; only {@code /login/oauth2/**} and {@code /login/webauthn}
 * belong to Spring Security.
 */
@Controller
public class SpaForwardingController {

    private static final String FIRST_SEGMENT =
            "^(?!api$|actuator$|error$|assets$|media$|images$|i18n$|h2-console$|oauth2$|webauthn$|ws$)[^.]*$";

    private static final List<String> BACKEND_PREFIXES = List.of(
            "/dev/oidc", "/login/oauth2", "/login/webauthn",
            // A44: OAuth provider endpoints (/oauth/consent stays an Angular route)
            "/oauth/authorize", "/oauth/token", "/oauth/userinfo");

    @GetMapping("/")
    public String root() {
        return "forward:/index.html";
    }

    @GetMapping(value = {"/{path:" + FIRST_SEGMENT + "}", "/{path:" + FIRST_SEGMENT + "}/**"})
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains(".") || BACKEND_PREFIXES.stream().anyMatch(prefix -> uri.equals(prefix) || uri.startsWith(prefix + "/"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
