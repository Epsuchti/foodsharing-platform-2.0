package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.service.GeocodingClient.GeoPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

/** Decision 171 (D2): address search with Photon, postcode fallback, cache and the GEOCODING breaker. */
class PhotonGeocodingClientTest {

    static final String HOUSE = "{\"features\":[{\"geometry\":{\"coordinates\":[8.5402,47.3769]}}]}";
    static final String POSTCODE = "{\"features\":[{\"geometry\":{\"coordinates\":[8.54,47.37]}}]}";
    static final String EMPTY = "{\"features\":[]}";

    private HttpServer server;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String addressAnswer = HOUSE;
    private IntegrationRegistry integrations;
    private PhotonGeocodingClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            queries.add(query);
            String body = query.contains("layer=postcode") ? POSTCODE : addressAnswer;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        AppProperties properties = new AppProperties();
        properties.getGeocoding().setPhotonUrl("http://127.0.0.1:" + server.getAddress().getPort());
        Clock clock = Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC);
        integrations = new IntegrationRegistry(properties, clock, mock(ApplicationEventPublisher.class));
        client = new PhotonGeocodingClient(properties, JsonMapper.builder().build(), clock, integrations);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void searchesTheFullAddressInHouseAndStreetLayers() {
        Optional<GeoPoint> point = client.geocodeAddress("CH", " Bahnhofstrasse  1 ", "8001", "Zürich");

        assertThat(point).contains(new GeoPoint(47.3769, 8.5402));
        assertThat(queries).singleElement().satisfies(q -> assertThat(q)
                .contains("layer=house&layer=street").contains("q=Bahnhofstrasse 1 8001 Zürich")
                .contains("countrycode=ch"));
    }

    @Test
    void unknownAddressFallsBackToThePostcode() {
        addressAnswer = EMPTY;

        assertThat(client.geocodeAddress("CH", "Nirgendweg 99", "8001", "Zürich")).contains(new GeoPoint(47.37, 8.54));
        assertThat(queries).hasSize(2);
        assertThat(queries.get(1)).contains("layer=postcode").contains("q=8001");
    }

    @Test
    void withoutStreetOnlyThePostcodeIsSearched() {
        assertThat(client.geocodeAddress("CH", " ", "8001", "Zürich")).contains(new GeoPoint(47.37, 8.54));
        assertThat(queries).singleElement().satisfies(q -> assertThat(q).contains("layer=postcode"));
    }

    @Test
    void answersAreCached() {
        client.geocodeAddress("CH", "Bahnhofstrasse 1", "8001", "Zürich");
        client.geocodeAddress("ch", "bahnhofstrasse 1", "8001", "zürich");

        assertThat(queries).hasSize(1);
    }

    @Test
    void failuresAndAnOpenBreakerAreUnavailable() {
        status = 500;
        assertThatThrownBy(() -> client.geocodeAddress("CH", "Bahnhofstrasse 1", "8001", "Zürich"))
                .isInstanceOf(GeocodingClient.GeocodingUnavailableException.class);

        status = 200;
        integrations.breaker(Integration.GEOCODING).forceOpen("test", "down");
        int before = queries.size();
        assertThatThrownBy(() -> client.geocodeAddress("CH", "Seestrasse 2", "8002", "Zürich"))
                .isInstanceOf(GeocodingClient.GeocodingUnavailableException.class);
        assertThat(queries).hasSize(before);
    }

    @Test
    void addressQueryJoinsTheFilledInParts() {
        assertThat(PhotonGeocodingClient.addressQuery("Hauptgasse 3", null, "Bern")).isEqualTo("Hauptgasse 3 Bern");
        assertThat(PhotonGeocodingClient.addressQuery(null, "3011", "")).isEqualTo("3011");
    }
}
