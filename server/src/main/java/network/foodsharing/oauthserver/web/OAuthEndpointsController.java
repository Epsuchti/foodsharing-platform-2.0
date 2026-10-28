package network.foodsharing.oauthserver.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import network.foodsharing.oauthserver.domain.OAuthScope;
import network.foodsharing.oauthserver.service.AuthorizationService;
import network.foodsharing.oauthserver.service.OAuthKeys;
import network.foodsharing.oauthserver.service.OAuthProtocolException;
import network.foodsharing.oauthserver.service.TokenService;
import network.foodsharing.oauthserver.service.UserInfoService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * OAuth2/OIDC framework endpoints (A44, listed in {@code x-framework-endpoints}; RFC wire formats, not the API
 * contract): {@code /oauth/authorize}, {@code /oauth/token}, {@code /oauth/userinfo} and the discovery documents.
 */
@Controller
public class OAuthEndpointsController {

    private final AuthorizationService authorization;
    private final TokenService tokens;
    private final UserInfoService userInfo;
    private final OAuthKeys keys;
    private final CurrentActorService currentActor;

    public OAuthEndpointsController(AuthorizationService authorization, TokenService tokens, UserInfoService userInfo,
                                    OAuthKeys keys, CurrentActorService currentActor) {
        this.authorization = authorization;
        this.tokens = tokens;
        this.userInfo = userInfo;
        this.keys = keys;
        this.currentActor = currentActor;
    }

    /** Signed-in users continue (consent page or code); others log in first and come back here. */
    @RequestMapping(value = "/oauth/authorize", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> authorize(@RequestParam Map<String, String> params, HttpServletRequest request) {
        authorization.requireEnabled();
        Optional<SessionPrincipal> principal = currentActor.principal();
        if (principal.isEmpty()) {
            String back = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            return redirect("/login?returnUrl=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
        }
        return redirect(authorization.authorize(params, principal.get().userId()));
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> form,
                                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                                     String authorizationHeader) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .body(tokens.token(form.toSingleValueMap(), authorizationHeader));
    }

    @RequestMapping(value = "/oauth/userinfo", method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> userinfo(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authorization.requireEnabled();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userInfo.userInfo(authorizationHeader));
    }

    @GetMapping(value = "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> discovery() {
        authorization.requireEnabled();
        String issuer = keys.issuer();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/oauth/authorize");
        doc.put("token_endpoint", issuer + "/oauth/token");
        doc.put("userinfo_endpoint", issuer + "/oauth/userinfo");
        doc.put("jwks_uri", issuer + "/.well-known/jwks.json");
        doc.put("response_types_supported", List.of("code"));
        doc.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("RS256"));
        doc.put("scopes_supported", java.util.Arrays.stream(OAuthScope.values()).map(Enum::name).toList());
        doc.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "none"));
        doc.put("code_challenge_methods_supported", List.of("S256"));
        doc.put("claims_supported", List.of("sub", "preferred_username", "name", "given_name", "family_name",
                "picture", "locale", "email", "email_verified", "region_ids", "region_ambassador"));
        return doc;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> jwks() {
        authorization.requireEnabled();
        return keys.jwks();
    }

    @ExceptionHandler(OAuthProtocolException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> protocolError(OAuthProtocolException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.error());
        body.put("error_description", e.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON);
        if (e.status() == HttpStatus.UNAUTHORIZED.value()) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "invalid_token".equals(e.error())
                    ? "Bearer error=\"invalid_token\"" : "Basic realm=\"oauth\"");
        }
        return response.body(body);
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location)
                .cacheControl(CacheControl.noStore()).build();
    }
}
