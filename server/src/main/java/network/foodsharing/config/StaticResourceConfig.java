package network.foodsharing.config;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular build. The content-hashed bundles (`main-<hash>.js`, `chunk-<hash>.js`, `styles-<hash>.css`,
 * fonts under `media/`) never change under the same name and are cached for a year (D11.5.3, Lighthouse); the
 * i18n catalogs and the preloaded fonts under `fonts/` are revalidated (ETag) because their names carry no hash.
 *
 * <p>MT1 (decision 159): {@code /tiles/**} serves the PMTiles basemap from {@code app.map.tiles-dir} with HTTP range
 * requests (206). No `.br` resolver and a media type outside {@code server.compression.mime-types}, so the bytes are
 * never compressed: a gzipped response would break the byte ranges.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    static final String PMTILES_MEDIA_TYPE = "application/vnd.pmtiles";
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
    /** One day; the ETag changes with the file, and the PMTiles client reloads when it sees a new one. */
    private static final CacheControl TILES = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final AppProperties properties;

    public StaticResourceConfig(AppProperties properties) {
        this.properties = properties;
    }

    /** Tomcat knows `.pmtiles`, so the resource handler sends {@value #PMTILES_MEDIA_TYPE}. */
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> pmtilesMimeMapping() {
        return factory -> factory.addMimeMappings(new MimeMappings(Map.of("pmtiles", PMTILES_MEDIA_TYPE)));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        precompressed(registry.addResourceHandler("/i18n/**")
                .addResourceLocations("classpath:/static/i18n/")
                .setCacheControl(CacheControl.noCache()));
        precompressed(registry.addResourceHandler("/main-*.js", "/chunk-*.js", "/polyfills-*.js", "/styles-*.css")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(IMMUTABLE));
        precompressed(registry.addResourceHandler("/media/**")
                .addResourceLocations("classpath:/static/media/")
                .setCacheControl(IMMUTABLE));
        // stable names (preloaded by index.html): revalidated
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(CacheControl.noCache());
        // null-safe: WebMvc slice tests mock AppProperties
        AppProperties.MapTiles map = properties.getMap();
        String tilesDir = map == null ? null : map.getTilesDir();
        if (tilesDir != null && !tilesDir.isBlank()) {
            String location = Path.of(tilesDir).toAbsolutePath().normalize().toUri().toString();
            registry.addResourceHandler("/tiles/**")
                    .addResourceLocations(location.endsWith("/") ? location : location + "/")
                    .setCacheControl(TILES)
                    .setEtagGenerator(StaticResourceConfig::tilesEtag)
                    .resourceChain(true)
                    .addResolver(new PathResourceResolver());
        }
    }

    /**
     * A strong ETag from modification time and size: a rebuilt file gets a new one (the PMTiles client ignores weak
     * ETags, and then could not notice a swapped file mid-session).
     */
    static String tilesEtag(Resource resource) {
        try {
            return "\"" + Long.toHexString(resource.lastModified()) + "-" + Long.toHexString(resource.contentLength())
                    + "\"";
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Serves the `.br` siblings written by client/scripts/precompress.mjs when the browser accepts Brotli. Only `br`
     * is offered: the resolver follows the order of the request header, and browsers list gzip first
     * (`gzip, deflate, br, zstd`); gzip-only clients get the on-the-fly compression (server.compression).
     */
    private static void precompressed(ResourceHandlerRegistration registration) {
        EncodedResourceResolver encoded = new EncodedResourceResolver();
        encoded.setContentCodings(List.of("br"));
        registration.resourceChain(true)
                .addResolver(encoded)
                .addResolver(new PathResourceResolver());
    }
}
