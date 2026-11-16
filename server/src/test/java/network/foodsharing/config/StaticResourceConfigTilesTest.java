package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

/**
 * MT1 (was StaticResourceTilesOffIT): {@code /tiles/**} is only mapped when {@code app.map.tiles-dir} is set; without
 * it nothing serves the path (and SpaForwardingControllerTest proves the SPA never answers it). The served bytes,
 * range requests and caching: StaticResourceTilesIT.
 */
class StaticResourceConfigTilesTest {

    @Test
    void withoutATilesDirectoryNothingIsMappedUnderTiles() {
        assertThat(registryFor("").hasMappingForPattern("/tiles/**")).isFalse();
        assertThat(registryFor("   ").hasMappingForPattern("/tiles/**")).isFalse();
    }

    @Test
    void aTilesDirectoryMapsTheTilesPath() {
        assertThat(registryFor("../e2e/fixtures/tiles/").hasMappingForPattern("/tiles/**")).isTrue();
    }

    private static ResourceHandlerRegistry registryFor(String tilesDir) {
        AppProperties properties = new AppProperties();
        properties.getMap().setTilesDir(tilesDir);
        MockServletContext servletContext = new MockServletContext();
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(new GenericWebApplicationContext(servletContext),
                servletContext);
        new StaticResourceConfig(properties).addResourceHandlers(registry);
        return registry;
    }
}
