package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * MT1 (decision 158) on a real Tomcat: {@code /tiles/basemap.pmtiles} answers range requests with 206, in its own
 * media type, never compressed (that would break the byte ranges), cached for a day with an ETag, and outside the
 * {@code /api/**} rate limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.api-rate-limit.requests-per-minute-per-ip=3")
class StaticResourceTilesIT {

    static final Path TILES_DIR = createTilesDir();
    /** A PMTiles v3 header start plus compressible filler, well above server.compression.min-response-size. */
    static final byte[] FILE = file();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void tilesDir(DynamicPropertyRegistry registry) {
        registry.add("app.map.tiles-dir", TILES_DIR::toString);
    }

    private static Path createTilesDir() {
        try {
            Path dir = Files.createTempDirectory("fs-tiles-it");
            Files.write(dir.resolve("basemap.pmtiles"), file());
            dir.toFile().deleteOnExit();
            dir.resolve("basemap.pmtiles").toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] file() {
        byte[] bytes = new byte[8192];
        Arrays.fill(bytes, (byte) 'a');
        byte[] magic = "PMTiles".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        bytes[7] = 3;
        return bytes;
    }

    static HttpResponse<byte[]> get(int port, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    @Test
    void aRangeRequestGets206WithTheRequestedBytesUncompressed() throws Exception {
        HttpResponse<byte[]> response = get(port, "/tiles/basemap.pmtiles", "Range", "bytes=0-126",
                "Accept-Encoding", "gzip, deflate, br");

        assertThat(response.statusCode()).isEqualTo(206);
        assertThat(response.headers().firstValue("Content-Range")).hasValue("bytes 0-126/" + FILE.length);
        assertThat(response.headers().firstValue("Accept-Ranges")).hasValue("bytes");
        assertThat(response.headers().firstValue("Content-Type")).hasValue(StaticResourceConfig.PMTILES_MEDIA_TYPE);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(response.body()).isEqualTo(Arrays.copyOfRange(FILE, 0, 127));
        assertThat(new String(response.body(), 0, 7, StandardCharsets.US_ASCII)).isEqualTo("PMTiles");
        assertThat(response.body()[7]).isEqualTo((byte) 3);
    }

    @Test
    void theWholeFileIsCachedForADayWithAnEtagAndNeverGzipped() throws Exception {
        HttpResponse<byte[]> response = get(port, "/tiles/basemap.pmtiles", "Accept-Encoding", "gzip");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("max-age=86400, public");
        assertThat(response.headers().firstValue("ETag")).isPresent();
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(response.body()).isEqualTo(FILE);
    }

    @Test
    void aMissingFileIs404() throws Exception {
        assertThat(get(port, "/tiles/missing.pmtiles").statusCode()).isEqualTo(404);
        assertThat(get(port, "/tiles/../application.properties").statusCode()).isNotEqualTo(200);
    }

    @Test
    void tileRequestsDoNotCountTowardsTheApiRateLimit() throws Exception {
        for (int i = 0; i < 6; i++) {
            assertThat(get(port, "/tiles/basemap.pmtiles", "Range", "bytes=0-126").statusCode()).isEqualTo(206);
        }
    }
}
