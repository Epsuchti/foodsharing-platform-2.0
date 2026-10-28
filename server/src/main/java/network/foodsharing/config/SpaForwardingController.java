package network.foodsharing.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forwards every non-API, non-file path to the Angular app so deep links work. Paths owned by the backend
 * (API, actuator, Spring Security's OAuth2/WebAuthn endpoints, the fake OIDC provider, H2 console) are never
 * forwarded. {@code /login} itself is an Angular route; only {@code /login/oauth2/**} and {@code /login/webauthn}
 * belong to Spring Security.
 *
 * <p>Prerendering (plan §11.2, D11.5.3): the client build prerenders the landing page once per UI language
 * ({@code static/<lang>/index.html}, hydrated by the browser) and writes the plain client-rendered shell to
 * {@code static/index.csr.html}. Anonymous visitors of {@code /} get the prerendered page in their language
 * ({@code fs.lang} cookie, then {@code Accept-Language}, then German); {@code /<lang>} serves that language
 * directly (the {@code hreflang} alternates). Visitors with a session cookie and every other route get the
 * client-rendered shell. Builds without a client (server tests) fall back to {@code index.html}.
 */
@Controller
public class SpaForwardingController {

    static final List<String> LANGUAGES = List.of("de", "gws", "fr", "it", "en");
    static final String LANGUAGE_COOKIE = "fs.lang";
    /** Placeholder the prerender writes into absolute URLs (canonical, hreflang); replaced with app.base-url. */
    static final String ORIGIN_PLACEHOLDER = "https://origin.invalid";

    private static final String FIRST_SEGMENT =
            "^(?!api$|actuator$|error$|assets$|media$|fonts$|images$|i18n$|h2-console$|oauth2$|webauthn$|ws$)[^.]*$";

    private static final List<String> BACKEND_PREFIXES = List.of(
            "/dev/oidc", "/login/oauth2", "/login/webauthn",
            // A44: OAuth provider endpoints (/oauth/consent stays an Angular route)
            "/oauth/authorize", "/oauth/token", "/oauth/userinfo");

    private final AppProperties properties;
    private final Map<String, Optional<String>> prerendered = new ConcurrentHashMap<>();
    private final String shell = new ClassPathResource("static/index.csr.html").exists()
            ? "forward:/index.csr.html"
            : "forward:/index.html";

    public SpaForwardingController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/")
    public Object root(HttpServletRequest request) {
        if (hasSession(request)) {
            return shell;
        }
        return landing(preferredLanguage(request));
    }

    @GetMapping(value = {"/{path:" + FIRST_SEGMENT + "}", "/{path:" + FIRST_SEGMENT + "}/**"})
    public Object forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String lang = uri.replaceAll("^/|/$", "");
        if (LANGUAGES.contains(lang)) {
            // /<lang>: the landing page in that language (hreflang alternates)
            return hasSession(request) ? shell : landing(lang);
        }
        if (uri.contains(".") || BACKEND_PREFIXES.stream().anyMatch(prefix -> uri.equals(prefix) || uri.startsWith(prefix + "/"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return shell;
    }

    private Object landing(String lang) {
        Optional<String> html = prerendered.computeIfAbsent(lang, this::readPrerendered);
        if (html.isEmpty()) {
            return shell;
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.VARY, "Accept-Language, Cookie")
                .header(HttpHeaders.CONTENT_LANGUAGE, lang)
                .body(html.get());
    }

    private Optional<String> readPrerendered(String lang) {
        ClassPathResource resource = new ClassPathResource("static/" + lang + "/index.html");
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            String origin = properties.getBaseUrl().replaceAll("/+$", "");
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).replace(ORIGIN_PLACEHOLDER, origin));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean hasSession(HttpServletRequest request) {
        return cookie(request, properties.getAuth().getCookieName()).isPresent();
    }

    static String preferredLanguage(HttpServletRequest request) {
        Optional<String> stored = cookie(request, LANGUAGE_COOKIE).filter(LANGUAGES::contains);
        if (stored.isPresent()) {
            return stored.get();
        }
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header != null) {
            try {
                for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                    // same mapping as the client's matchBrowserLanguage (languages.ts): gsw → gws, de-CH → de
                    String primary = range.getRange().toLowerCase(Locale.ROOT).split("-")[0];
                    if (primary.equals("gsw")) {
                        return "gws";
                    }
                    if (LANGUAGES.contains(primary)) {
                        return primary;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // malformed header: default language
            }
        }
        return "de";
    }

    private static Optional<String> cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(c -> name.equals(c.getName())).map(Cookie::getValue).findFirst();
    }
}
