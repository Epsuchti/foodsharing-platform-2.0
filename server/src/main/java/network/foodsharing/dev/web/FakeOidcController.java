package network.foodsharing.dev.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import network.foodsharing.dev.service.FakeOidcProvider;
import network.foodsharing.dev.service.FakeOidcProvider.Account;
import network.foodsharing.dev.service.FakeOidcProvider.OidcError;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * HTTP side of the fake OIDC provider at {@code /dev/oidc/**} (framework endpoint, not part of the OpenAPI contract;
 * a plain {@code @Controller} because it serves an HTML page and OAuth-shaped JSON instead of {@code ApiError}s).
 * The issuer is derived from the request ({@code <scheme>://<host>:<port>/dev/oidc}), which equals the configured
 * {@code app.auth.google.issuer} in local/e2e.
 */
@Controller
@Profile({"local", "e2e"})
public class FakeOidcController {

    static final String BASE = "/dev/oidc";

    private final FakeOidcProvider provider;

    public FakeOidcController(FakeOidcProvider provider) {
        this.provider = provider;
    }

    @GetMapping(value = BASE + "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> discovery() {
        return provider.discovery(issuer());
    }

    @GetMapping(value = BASE + "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> jwks() {
        return provider.jwks();
    }

    /** The account chooser: seed users with a Google identity, a "new Google user" form and cancel. */
    @GetMapping(BASE + "/authorize")
    public String authorizePage(@RequestParam(name = "response_type", required = false) String responseType,
                                @RequestParam(name = "client_id", required = false) String clientId,
                                @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                @RequestParam(name = "scope", required = false) String scope,
                                @RequestParam(name = "state", required = false) String state,
                                @RequestParam(name = "nonce", required = false) String nonce,
                                @RequestParam(name = "login_hint", required = false) String loginHint,
                                HttpServletRequest request, Model model) {
        model.addAttribute("clientId", clientId);
        model.addAttribute("redirectUri", redirectUri);
        model.addAttribute("scope", scope);
        model.addAttribute("state", state);
        model.addAttribute("nonce", nonce);
        model.addAttribute("loginHint", loginHint);
        model.addAttribute("accounts", provider.seedAccounts());
        model.addAttribute("action", BASE + "/authorize");
        String error = validateAuthorizeRequest(responseType == null ? "code" : responseType, clientId, redirectUri, request);
        model.addAttribute("error", error);
        return "dev/oidc-authorize";
    }

    /** Submits the chooser: redirects back to the client with {@code code} (or {@code error=access_denied}). */
    @PostMapping(value = BASE + "/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authorize(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
        String clientId = form.getFirst("client_id");
        String redirectUri = form.getFirst("redirect_uri");
        String state = form.getFirst("state");
        String error = validateAuthorizeRequest("code", clientId, redirectUri, request);
        if (error != null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(error);
        }
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(redirectUri);
        String choice = form.getFirst("choice");
        if ("cancel".equals(choice)) {
            target.queryParam("error", "access_denied");
        } else {
            Account account;
            if ("new".equals(choice)) {
                String email = form.getFirst("email");
                if (email == null || !email.contains("@")) {
                    return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("A valid email is required");
                }
                account = provider.newAccount(email, form.getFirst("name"), form.getFirst("locale"),
                        !form.containsKey("unverified"));
            } else {
                account = provider.seedAccount(form.getFirst("account")).orElse(null);
                if (account == null) {
                    return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Unknown account");
                }
            }
            target.queryParam("code", provider.issueCode(clientId, redirectUri, form.getFirst("nonce"),
                    form.getFirst("scope"), account));
        }
        if (state != null && !state.isEmpty()) {
            target.queryParam("state", state);
        }
        URI location = target.build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @PostMapping(value = BASE + "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> form,
                                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                                     String authorization) {
        String clientId = form.getFirst("client_id");
        String clientSecret = form.getFirst("client_secret");
        if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).strip()),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon > 0) {
                clientId = URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
                clientSecret = URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8);
            }
        }
        Map<String, Object> body = provider.exchange(form.getFirst("grant_type"), form.getFirst("code"),
                form.getFirst("redirect_uri"), clientId, clientSecret, issuer());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @RequestMapping(value = BASE + "/userinfo", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> userInfo(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                        String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).strip() : null;
        return provider.userInfo(token);
    }

    @ExceptionHandler(OidcError.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> oidcError(OidcError error) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(error.status()).contentType(MediaType.APPLICATION_JSON);
        if (error.status() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + error.error() + "\"");
        }
        return response.body(error.body());
    }

    private static String issuer() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(BASE).build().toUriString();
    }

    /** Null when valid. Redirects only go back to this host or localhost (the Angular dev server runs on :4200). */
    private static String validateAuthorizeRequest(String responseType, String clientId, String redirectUri,
                                                   HttpServletRequest request) {
        if (!"code".equals(responseType)) {
            return "Only response_type=code is supported";
        }
        if (clientId == null || clientId.isBlank()) {
            return "client_id is required";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return "redirect_uri is required";
        }
        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (IllegalArgumentException e) {
            return "redirect_uri is not a valid URI";
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.endsWith(".localhost")
                || host.equalsIgnoreCase(request.getServerName());
        if (!http || !local) {
            return "redirect_uri must point to this host or localhost";
        }
        return null;
    }
}
