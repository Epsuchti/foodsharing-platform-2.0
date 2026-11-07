package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import network.foodsharing.region.service.GeocodingClient.GeoPoint;
import org.junit.jupiter.api.Test;

/** Decision 171 (D2): offline, an address resolves to its postcode. */
class StaticGeocodingClientTest {

    private final StaticGeocodingClient client = new StaticGeocodingClient(Map.of("CH:8001", new GeoPoint(47.37, 8.54)));

    @Test
    void addressFallsBackToThePostcode() {
        assertThat(client.geocodeAddress("CH", "Bahnhofstrasse 1", " 8001 ", "Zürich")).contains(new GeoPoint(47.37, 8.54));
        assertThat(client.geocodeAddress(null, null, "8001", null)).contains(new GeoPoint(47.37, 8.54));
    }

    @Test
    void unknownPostcodeIsEmpty() {
        assertThat(client.geocodeAddress("CH", "Bahnhofstrasse 1", "9999", "Nirgendwo")).isEmpty();
        assertThat(client.geocodeAddress("CH", "Bahnhofstrasse 1", null, "Zürich")).isEmpty();
    }
}
