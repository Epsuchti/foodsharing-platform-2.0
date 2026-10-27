package network.foodsharing.region.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Offline geocoding from {@code db/seed/region/geocoding.csv} ({@code country,postcode,latitude,longitude}); used in
 * local, e2e and test ({@code app.geocoding.provider=static}).
 */
@Component
@ConditionalOnProperty(name = "app.geocoding.provider", havingValue = "static")
public class StaticGeocodingClient implements GeocodingClient {

    static final String RESOURCE = "db/seed/region/geocoding.csv";

    private final Map<String, GeoPoint> points;

    public StaticGeocodingClient() {
        this(load());
    }

    StaticGeocodingClient(Map<String, GeoPoint> points) {
        this.points = Map.copyOf(points);
    }

    @Override
    public Optional<GeoPoint> geocodePostcode(String country, String postcode) {
        if (postcode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(points.get(key(country, postcode)));
    }

    static String key(String country, String postcode) {
        return (country == null ? "CH" : country.toUpperCase(Locale.ROOT)) + ":" + postcode.strip();
    }

    private static Map<String, GeoPoint> load() {
        Map<String, GeoPoint> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = line.split(",");
                result.put(key(cells[0], cells[1]),
                        new GeoPoint(Double.parseDouble(cells[2]), Double.parseDouble(cells[3])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
        return result;
    }
}
