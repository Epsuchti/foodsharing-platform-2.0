package network.foodsharing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.security.SessionAuthenticationToken;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/**
 * D17.2: /api/** counts per account with a session and per IP without one; a block answers 429 with Retry-After;
 * webhooks and /api/dev are skipped; a limit of 0 turns the filter off; one history entry per block.
 */
class ApiRateLimitFilterTest {

    private static final Instant START = Instant.parse("2026-11-02T09:00:00Z");

    private final MutableClock clock = new MutableClock(java.time.Clock.fixed(START, ZoneOffset.UTC), START);
    private final List<RateLimitBlockRegistry.Block> recorded = new ArrayList<>();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private ApiRateLimitFilter filter(int perUser, int perIp) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getApiRateLimit().setRequestsPerMinutePerUser(perUser);
        properties.getSecurity().getApiRateLimit().setRequestsPerMinutePerIp(perIp);
        return new ApiRateLimitFilter(new RateLimitBlockRegistry(properties, clock), recorded::add,
                new ApiErrorResponseWriter(JsonMapper.builder().build()));
    }

    private static MockHttpServletResponse call(ApiRateLimitFilter filter, String path, String ip, UUID userId)
            throws Exception {
        if (userId == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.getContext().setAuthentication(SessionAuthenticationToken.of(
                    new SessionPrincipal(userId, UUID.randomUUID(), Role.FOODSAVER, userId + "@example.org")));
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void aSessionCountsPerUserSoTwoUsersOnOneIpDoNotAffectEachOther() throws Exception {
        ApiRateLimitFilter filter = filter(3, 1);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "/api/users/me", "10.0.0.1", alice).getStatus()).isEqualTo(200);
        }
        assertThat(call(filter, "/api/users/me", "10.0.0.2", alice).getStatus()).as("any IP").isEqualTo(429);
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "/api/users/me", "10.0.0.1", bob).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void withoutASessionRequestsCountPerIp() throws Exception {
        ApiRateLimitFilter filter = filter(100, 2);
        assertThat(call(filter, "/api/public/app-info", "10.0.0.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/users/me", "10.0.0.1", null).getStatus()).isEqualTo(200);
        assertThat(call(filter, "/api/public/app-info", "10.0.0.1", null).getStatus()).isEqualTo(429);
        assertThat(call(filter, "/api/public/app-info", "10.0.0.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void anIpBlockDoesNotAffectASignedInRequestFromThatIp() throws Exception {
        ApiRateLimitFilter filter = filter(100, 1);
        call(filter, "/api/public/app-info", "10.0.0.1", null);
        assertThat(call(filter, "/api/public/app-info", "10.0.0.1", null).getStatus()).isEqualTo(429);
        assertThat(call(filter, "/api/users/me", "10.0.0.1", UUID.randomUUID()).getStatus()).isEqualTo(200);
    }

    @Test
    void the429CarriesRetryAfterWithTheRemainingSeconds() throws Exception {
        ApiRateLimitFilter filter = filter(100, 1);
        call(filter, "/api/public/app-info", "10.0.0.1", null);
        MockHttpServletResponse blocked = call(filter, "/api/public/app-info", "10.0.0.1", null);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("TOO_MANY_REQUESTS");

        clock.fixAt(START.plusMillis(20_500));
        MockHttpServletResponse later = call(filter, "/api/public/app-info", "10.0.0.1", null);
        assertThat(later.getStatus()).isEqualTo(429);
        assertThat(later.getHeader("Retry-After")).as("39.5 s rounded up").isEqualTo("40");
    }

    @Test
    void webhooksAndDevAreSkippedAndALimitOfZeroTurnsTheFilterOff() throws Exception {
        ApiRateLimitFilter filter = filter(1, 1);
        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "/api/public/telegram/webhook/s3cret", "10.0.0.1", null).getStatus()).isEqualTo(200);
            assertThat(call(filter, "/api/public/mailbox/inbound/s3cret", "10.0.0.1", null).getStatus()).isEqualTo(200);
            assertThat(call(filter, "/api/dev/seed/reset", "10.0.0.1", null).getStatus()).isEqualTo(200);
            assertThat(call(filter, "/some/spa/route", "10.0.0.1", null).getStatus()).isEqualTo(200);
        }
        ApiRateLimitFilter off = filter(0, 0);
        for (int i = 0; i < 5; i++) {
            assertThat(call(off, "/api/public/app-info", "10.0.0.1", null).getStatus()).isEqualTo(200);
            assertThat(call(off, "/api/users/me", "10.0.0.1", UUID.randomUUID()).getStatus()).isEqualTo(200);
        }
        assertThat(recorded).isEmpty();
    }

    @Test
    void theHistoryIsWrittenOncePerBlock() throws Exception {
        ApiRateLimitFilter filter = filter(100, 1);
        call(filter, "/api/public/app-info", "10.0.0.1", null);
        for (int i = 0; i < 10; i++) {
            assertThat(call(filter, "/api/public/app-info", "10.0.0.1", null).getStatus()).isEqualTo(429);
        }
        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().subject()).isEqualTo("10.0.0.1");
        assertThat(recorded.getFirst().level()).isEqualTo(1);

        clock.fixAt(START.plusSeconds(121));
        call(filter, "/api/public/app-info", "10.0.0.1", null);
        assertThat(call(filter, "/api/public/app-info", "10.0.0.1", null).getStatus()).isEqualTo(429);
        assertThat(recorded).extracting(RateLimitBlockRegistry.Block::level).containsExactly(1, 2);
    }
}
