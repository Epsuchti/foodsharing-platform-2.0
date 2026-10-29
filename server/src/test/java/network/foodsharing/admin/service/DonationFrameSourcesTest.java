package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import network.foodsharing.admin.domain.ConfigurationKey;
import org.junit.jupiter.api.Test;

/** G4: the donation iframe URLs feed the CSP frame-src, cached for one TTL and dropped on a settings change. */
class DonationFrameSourcesTest {

    private final ConfigurationService configuration = mock(ConfigurationService.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-02T09:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final DonationFrameSources sources = new DonationFrameSources(configuration, clock);

    @Test
    void returnsTheSetIframeUrlsAndCachesThemForOneTtl() {
        when(configuration.url(ConfigurationKey.DONATION_IFRAME_ONE_TIME_URL)).thenReturn("https://a.example/x");
        when(configuration.url(ConfigurationKey.DONATION_IFRAME_SELF_SERVICE_URL)).thenReturn("https://b.example/y");

        assertThat(sources.frameUrls()).containsExactly("https://a.example/x", "https://b.example/y");

        when(configuration.url(ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL)).thenReturn("https://c.example/z");
        now.set(now.get().plus(DonationFrameSources.TTL).minusSeconds(1));
        assertThat(sources.frameUrls()).hasSize(2);
        verify(configuration, times(1)).url(ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL);

        now.set(now.get().plusSeconds(1));
        assertThat(sources.frameUrls()).containsExactly("https://a.example/x", "https://c.example/z",
                "https://b.example/y");
    }

    @Test
    void invalidateReloadsAtOnce() {
        assertThat(sources.frameUrls()).isEmpty();
        when(configuration.url(ConfigurationKey.DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL))
                .thenReturn("https://d.example/");
        assertThat(sources.frameUrls()).isEmpty();
        sources.invalidate();
        assertThat(sources.frameUrls()).containsExactly("https://d.example/");
    }
}
