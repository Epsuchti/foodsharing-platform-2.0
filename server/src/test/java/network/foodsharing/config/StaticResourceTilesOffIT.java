package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** MT1: without {@code app.map.tiles-dir} nothing is served under {@code /tiles/**} (and the SPA never answers it). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.map.tiles-dir=")
class StaticResourceTilesOffIT {

    @LocalServerPort
    int port;

    @Test
    void tilesAreNotServed() throws Exception {
        assertThat(StaticResourceTilesIT.get(port, "/tiles/basemap.pmtiles", "Range", "bytes=0-126").statusCode())
                .isEqualTo(404);
        assertThat(StaticResourceTilesIT.get(port, "/tiles/").statusCode()).isEqualTo(404);
    }
}
