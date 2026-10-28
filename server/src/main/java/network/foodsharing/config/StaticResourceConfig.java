package network.foodsharing.config;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

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
