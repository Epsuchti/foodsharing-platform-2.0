package network.foodsharing.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A tiny OIDC provider for integration tests (token, JWKS, userinfo endpoints on an embedded JDK HTTP server). The
 * test drives the authorization step itself: it reads {@code state}/{@code nonce} from the app's redirect, calls
 * {@link #issueCode} and then hits the app's callback with the returned code.
 */
public final class OidcProviderStub implements AutoCloseable {

    public static final String CLIENT_ID = "dev-client";
    public static final String CLIENT_SECRET = "dev-secret";

    private final HttpServer server;
    private final RSAKey key;
    private final Map<String, Map<String, Object>> claimsByCode = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> claimsByAccessToken = new ConcurrentHashMap<>();
    private volatile Instant now = java.time.Clock.systemUTC().instant();

    public OidcProviderStub() {
        try {
            key = new RSAKeyGenerator(2048).keyID("stub-key").generate();
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException | JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/token", this::token);
        server.createContext("/jwks", exchange -> respond(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/userinfo", this::userInfo);
        server.createContext("/authorize", exchange -> respond(exchange, 200, "{}"));
        server.start();
    }

    public String issuer() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The instant used for iat/exp (real time, like a real provider; decision 34). */
    public void now(Instant instant) {
        this.now = instant;
    }

    /** Registers an authorization code that yields an id_token with these claims (plus iss, aud, iat, exp). */
    public String issueCode(Map<String, Object> claims) {
        String code = "code-" + claimsByCode.size() + "-" + System.nanoTime();
        claimsByCode.put(code, new LinkedHashMap<>(claims));
        return code;
    }

    private void token(HttpExchange exchange) throws IOException {
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        if (!expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        Map<String, String> form = form(exchange.getRequestBody());
        Map<String, Object> claims = claimsByCode.remove(form.get("code"));
        if (claims == null) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        String accessToken = "at-" + System.nanoTime();
        claimsByAccessToken.put(accessToken, claims);
        String body = "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                + "\"scope\":\"openid email profile\",\"id_token\":\"" + idToken(claims) + "\"}";
        respond(exchange, 200, body);
    }

    private void userInfo(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        Map<String, Object> claims = authorization == null ? null
                : claimsByAccessToken.get(authorization.replaceFirst("^Bearer ", ""));
        if (claims == null) {
            respond(exchange, 401, "{\"error\":\"invalid_token\"}");
            return;
        }
        Map<String, Object> userInfo = new LinkedHashMap<>(claims);
        userInfo.remove("nonce");
        respond(exchange, 200, com.nimbusds.jose.util.JSONObjectUtils.toJSONString(userInfo));
    }

    private String idToken(Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer(issuer())
                    .audience(List.of(CLIENT_ID))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)));
            claims.forEach(builder::claim);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    builder.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, String> form(InputStream body) throws IOException {
        String raw = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        return java.util.Arrays.stream(raw.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8), (a, b) -> a));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
