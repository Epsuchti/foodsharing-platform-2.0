package network.foodsharing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** D17.2: /api/public/** is limited per client IP (429 + Retry-After); other paths and other IPs are not. */
class PublicApiRateLimitFilterTest {

    private static PublicApiRateLimitFilter filter(int limit) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().setPublicApiRequestsPerMinutePerIp(limit);
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
        return new PublicApiRateLimitFilter(properties, clock, new ApiErrorResponseWriter(JsonMapper.builder().build()));
    }

    private static MockHttpServletResponse call(PublicApiRateLimitFilter filter, String path, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void publicApiIsLimitedPerIp() throws Exception {
        PublicApiRateLimitFilter filter = filter(3);
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "/api/public/app-info", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse limited = call(filter, "/api/public/regions/tree", "10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(call(filter, "/api/public/app-info", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void otherPathsAndADisabledLimitAreNotCounted() throws Exception {
        PublicApiRateLimitFilter filter = filter(1);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "/api/users/me", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(call(filter, "/", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(call(filter, "/api/public/telegram/webhook/s3cret", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        PublicApiRateLimitFilter off = filter(0);
        for (int i = 0; i < 5; i++) {
            assertThat(call(off, "/api/public/app-info", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }
}
