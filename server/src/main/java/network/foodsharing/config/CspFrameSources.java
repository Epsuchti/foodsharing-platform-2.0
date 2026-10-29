package network.foodsharing.config;

import java.util.Collection;

/**
 * SPI for runtime {@code frame-src} sources of the Content-Security-Policy (G4): URLs of external pages the SPA embeds
 * in iframes (e.g. the {@code donation.iframe-*} configuration). Only the https origins of the URLs end up in the
 * header. Called for every response, so implementations cache.
 */
public interface CspFrameSources {

    Collection<String> frameUrls();
}
