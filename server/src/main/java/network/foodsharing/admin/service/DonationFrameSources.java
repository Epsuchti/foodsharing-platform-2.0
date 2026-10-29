package network.foodsharing.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.config.CspFrameSources;
import org.springframework.stereotype.Component;

/**
 * The donation iframe URLs ({@code donation.iframe-*}, C7/C8) as CSP frame sources (G4). The header is written on
 * every response, so the URLs are cached for {@link #TTL}; {@link DonationSettingsService#update} drops the cache, a
 * change through the generic configuration editor shows after at most one TTL.
 */
@Component
public class DonationFrameSources implements CspFrameSources {

    static final Duration TTL = Duration.ofMinutes(1);

    private static final List<ConfigurationKey> KEYS = List.of(ConfigurationKey.DONATION_IFRAME_ONE_TIME_URL,
            ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL, ConfigurationKey.DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL,
            ConfigurationKey.DONATION_IFRAME_SELF_SERVICE_URL);

    private record Cached(List<String> urls, Instant loadedAt) {
    }

    private final ConfigurationService configuration;
    private final Clock clock;
    private volatile Cached cached;

    public DonationFrameSources(ConfigurationService configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    public Collection<String> frameUrls() {
        Instant now = clock.instant();
        Cached current = cached;
        if (current == null || !now.isBefore(current.loadedAt().plus(TTL))) {
            current = new Cached(KEYS.stream().map(configuration::url).filter(Objects::nonNull).toList(), now);
            cached = current;
        }
        return current.urls();
    }

    /** Forget the cached URLs (after the donation settings changed). */
    public void invalidate() {
        cached = null;
    }
}
