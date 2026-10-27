package network.foodsharing.testsupport;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * A minimal "browser" for {@code RANDOM_PORT} integration tests: keeps cookies, never follows redirects and sends
 * the {@code XSRF-TOKEN} cookie back as {@code X-XSRF-TOKEN} header on unsafe requests (like Angular does).
 */
public class BrowserClient {

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client;

    public BrowserClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public HttpResponse<String> get(String pathOrUrl) {
        return send(HttpRequest.newBuilder(uri(pathOrUrl)).GET());
    }

    public HttpResponse<String> postJson(String path, String json) {
        return send(withCsrf(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))));
    }

    public HttpResponse<String> postJsonWithoutCsrf(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    public HttpResponse<String> delete(String path) {
        return send(withCsrf(HttpRequest.newBuilder(uri(path)).DELETE()));
    }

    /** Loads the CSRF cookie (any GET does; the session bootstrap is the natural one). */
    public BrowserClient bootstrap() {
        get("/api/auth/session");
        return this;
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .filter(cookie -> !cookie.hasExpired())
                .map(HttpCookie::getValue)
                .filter(value -> !value.isEmpty())
                .findFirst();
    }

    public void setCookie(String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        cookie.setVersion(0);
        cookies.getCookieStore().add(URI.create(baseUrl), cookie);
    }

    private HttpRequest.Builder withCsrf(HttpRequest.Builder builder) {
        cookie("XSRF-TOKEN").ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        return builder;
    }

    private URI uri(String pathOrUrl) {
        return URI.create(pathOrUrl.startsWith("http") ? pathOrUrl : baseUrl + pathOrUrl);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
