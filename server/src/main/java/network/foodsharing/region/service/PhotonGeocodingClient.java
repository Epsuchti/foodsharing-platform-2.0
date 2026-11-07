package network.foodsharing.region.service;

import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Postcode and address geocoding with Photon (komoot, OSM based): timeouts and a 24 h cache; failures go to the GEOCODING breaker
 * of the {@link IntegrationRegistry} (A26), which fails fast while open. {@link #probe()} is the registry's probe.
 */
@Component
@ConditionalOnProperty(name = "app.geocoding.provider", havingValue = "photon", matchIfMissing = true)
public class PhotonGeocodingClient implements GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(PhotonGeocodingClient.class);

    private record CacheEntry(Optional<GeoPoint> point, Instant expiresAt) {
    }

    private final AppProperties.Geocoding settings;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final IntegrationRegistry integrations;

    public PhotonGeocodingClient(AppProperties properties, ObjectMapper objectMapper, Clock clock,
                                 IntegrationRegistry integrations) {
        this.integrations = integrations;
        this.settings = properties.getGeocoding();
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(settings.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Optional<GeoPoint> geocodePostcode(String country, String postcode) {
        if (postcode == null || postcode.isBlank()) {
            return Optional.empty();
        }
        String key = StaticGeocodingClient.key(country, postcode);
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.point();
        }
        Optional<GeoPoint> point = call(key, () -> fetchUnchecked(country, postcode.strip(), "&layer=postcode"));
        cache.put(key, new CacheEntry(point, now.plus(Duration.ofHours(settings.getCacheHours()))));
        return point;
    }

    @Override
    public Optional<GeoPoint> geocodeAddress(String country, String street, String postcode, String city) {
        String query = addressQuery(street, postcode, city);
        if (street == null || street.isBlank() || query.isEmpty()) {
            return geocodePostcode(country, postcode);
        }
        String key = "address:" + (country == null ? "CH" : country.toUpperCase(Locale.ROOT)) + ":"
                + query.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        CacheEntry cached = cache.get(key);
        Optional<GeoPoint> point;
        if (cached != null && cached.expiresAt().isAfter(now)) {
            point = cached.point();
        } else {
            point = call(key, () -> fetchUnchecked(country, query, "&layer=house&layer=street"));
            cache.put(key, new CacheEntry(point, now.plus(Duration.ofHours(settings.getCacheHours()))));
        }
        return point.isPresent() ? point : geocodePostcode(country, postcode);
    }

    /** {@code street postcode city}, blanks skipped and whitespace collapsed. */
    static String addressQuery(String street, String postcode, String city) {
        StringBuilder query = new StringBuilder();
        for (String part : new String[] {street, postcode, city}) {
            if (part != null && !part.isBlank()) {
                query.append(query.isEmpty() ? "" : " ").append(part.strip());
            }
        }
        return query.toString().replaceAll("\\s+", " ");
    }

    private Optional<GeoPoint> call(String key, java.util.function.Supplier<Optional<GeoPoint>> fetch) {
        try {
            return integrations.call(Integration.GEOCODING, "photon", fetch);
        } catch (ApiException e) {
            throw new GeocodingUnavailableException("geocoding circuit open", e);
        } catch (RuntimeException e) {
            log.warn("Photon geocoding failed for {}: {}", key, e.toString());
            throw new GeocodingUnavailableException("geocoding failed", e.getCause() == null ? e : e.getCause());
        }
    }

    /** A26 probe: "8001" (Zürich) resolves, called directly (not through the breaker). */
    public boolean probe() {
        try {
            return fetch("CH", "8001", "&layer=postcode").isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<GeoPoint> fetchUnchecked(String country, String q, String layers) {
        try {
            return fetch(country == null ? "CH" : country, q, layers);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<GeoPoint> fetch(String country, String q, String layers) throws Exception {
        String query = URLEncoder.encode(q, StandardCharsets.UTF_8);
        URI uri = URI.create(settings.getPhotonUrl() + "/api?limit=1" + layers + "&q=" + query
                + "&countrycode=" + URLEncoder.encode(country.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(settings.getReadTimeoutMs()))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Photon answered " + response.statusCode());
        }
        JsonNode features = objectMapper.readTree(response.body()).path("features");
        if (!features.isArray() || features.isEmpty()) {
            return Optional.empty();
        }
        JsonNode coordinates = features.get(0).path("geometry").path("coordinates");
        return Optional.of(new GeoPoint(coordinates.get(1).asDouble(), coordinates.get(0).asDouble()));
    }
}
