package network.foodsharing.intropickup.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Reads the guide's own foodsharing calendar (reference {@code IcalImportService}) to offer pickups as EinAb
 * candidates. SSRF-safe: https only, allowed hosts (and their subdomains) only, no user info, port 443, redirects are
 * not followed, 2 MB cap. Failures yield an empty list. In local/e2e the app's own base URL is allowed as well
 * ({@code app.intropickup.ical-allow-own-base-url}, EA16); production keeps the https/443 host list only.
 */
@Service
public class IcalImportService {

    public record Candidate(Instant startAt, Instant endAt, String summary, String location) {
    }

    /** Downloads a feed (replaceable in tests). */
    @FunctionalInterface
    public interface Fetcher {
        InputStream fetch(URI uri) throws IOException;
    }

    static final int MAX_FEED_BYTES = 2 * 1024 * 1024;
    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter ZULU = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppProperties properties;
    private final Clock clock;
    private Fetcher fetcher = IcalImportService::download;

    public IcalImportService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void setFetcher(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public boolean isAllowedLink(String link) {
        return isAllowedLink(link, properties.getIntropickup().getIcalAllowedHosts())
                || (properties.getIntropickup().isIcalAllowOwnBaseUrl()
                    && isOwnOrigin(link, properties.getBaseUrl()));
    }

    /**
     * local/e2e only ({@code app.intropickup.ical-allow-own-base-url}): same scheme, host and port as the app's own
     * base URL and no user info, so the dev iCal fixture can be imported end to end (EA16).
     */
    public static boolean isOwnOrigin(String link, String baseUrl) {
        if (link == null || link.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(link.trim());
            URI base = new URI(baseUrl.trim());
            return uri.getHost() != null && uri.getUserInfo() == null && base.getScheme() != null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getScheme().equalsIgnoreCase(base.getScheme())
                    && uri.getHost().equalsIgnoreCase(base.getHost()) && port(uri) == port(base);
        } catch (Exception e) {
            return false;
        }
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** https, an allowed host or a subdomain of one, no user info, default port. */
    public static boolean isAllowedLink(String link, Collection<String> allowedHosts) {
        if (link == null || link.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(link.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT))
                    .anyMatch(allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed));
        } catch (Exception e) {
            return false;
        }
    }

    /** Future events of the feed; empty when the link is not allowed or the feed cannot be read. */
    public List<Candidate> candidates(String link) {
        if (!isAllowedLink(link)) {
            return List.of();
        }
        try (InputStream body = fetcher.fetch(URI.create(link.trim()))) {
            byte[] bytes = body.readNBytes(MAX_FEED_BYTES + 1);
            if (bytes.length > MAX_FEED_BYTES) {
                return List.of();
            }
            return parse(new String(bytes, StandardCharsets.UTF_8), clock.instant());
        } catch (Exception e) {
            return List.of();
        }
    }

    static List<Candidate> parse(String feed, Instant now) throws IOException {
        List<Candidate> result = new ArrayList<>();
        Instant start = null;
        Instant end = null;
        String summary = null;
        String location = null;
        for (String line : unfold(new BufferedReader(new StringReader(feed)))) {
            String name = line.contains(":") ? line.substring(0, line.indexOf(':')) : line;
            String value = line.contains(":") ? line.substring(line.indexOf(':') + 1) : "";
            String property = name.contains(";") ? name.substring(0, name.indexOf(';')) : name;
            switch (property) {
                case "BEGIN" -> {
                    if (value.equals("VEVENT")) {
                        start = null;
                        end = null;
                        summary = null;
                        location = null;
                    }
                }
                case "DTSTART" -> start = parseDateTime(value);
                case "DTEND" -> end = parseDateTime(value);
                case "SUMMARY" -> summary = unescape(value).trim();
                case "LOCATION" -> location = unescape(value).trim();
                case "END" -> {
                    if (value.equals("VEVENT") && start != null && !start.isBefore(now)) {
                        result.add(new Candidate(start, end,
                                summary == null || summary.isBlank() ? "Imported event" : summary,
                                location == null || location.isBlank() ? null : location));
                    }
                }
                default -> {
                    // other properties are not needed
                }
            }
        }
        result.sort(java.util.Comparator.comparing(Candidate::startAt));
        return result;
    }

    private static List<String> unfold(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        String current = null;
        String raw;
        while ((raw = reader.readLine()) != null) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && current != null) {
                current += raw.substring(1);
                continue;
            }
            if (current != null) {
                lines.add(current);
            }
            current = raw;
        }
        if (current != null) {
            lines.add(current);
        }
        return lines;
    }

    static Instant parseDateTime(String raw) {
        try {
            String value = raw.trim();
            if (value.endsWith("Z")) {
                return LocalDateTime.parse(value, ZULU).toInstant(ZoneOffset.UTC);
            }
            if (value.length() == 8) {
                return LocalDate.parse(value, DATE).atStartOfDay(ZURICH).toInstant();
            }
            return LocalDateTime.parse(value, LOCAL).atZone(ZURICH).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i == value.length() - 1) {
                out.append(c);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case 'n', 'N' -> out.append('\n');
                case ',', ';', '\\' -> out.append(escaped);
                default -> out.append('\\').append(escaped);
            }
        }
        return out.toString();
    }

    /** Plain GET without following redirects (a redirect could leave the allowed hosts). */
    private static InputStream download(URI uri) throws IOException {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("Unexpected iCal response " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
