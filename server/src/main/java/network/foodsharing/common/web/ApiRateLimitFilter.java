package network.foodsharing.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.security.SessionPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * D17.2 (OWASP A04): ceiling for every {@code /api/**} request with escalating blocks
 * ({@link RateLimitBlockRegistry}). Runs right after the session cookie authentication: a request with a valid
 * session counts toward its account (from any IP), one without toward its client IP. A blocked account or IP gets
 * 429 TOO_MANY_REQUESTS with {@code Retry-After} until the block ends; every new block is recorded in the history.
 * Not counted: the secret webhooks ({@link PublicApiRateLimitFilter#WEBHOOKS}) and {@code /api/dev/**}. Created by
 * {@code config.SecurityConfig}; deliberately not a bean (no second servlet registration).
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ApiRateLimitFilter.class);

    private final RateLimitBlockRegistry registry;
    private final RateLimitBlockRecorder recorder;
    private final ApiErrorResponseWriter errorWriter;

    public ApiRateLimitFilter(RateLimitBlockRegistry registry, RateLimitBlockRecorder recorder,
                              ApiErrorResponseWriter errorWriter) {
        this.registry = registry;
        this.recorder = recorder;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !registry.enabled() || !path.startsWith("/api/") || path.startsWith("/api/dev/")
                || PublicApiRateLimitFilter.WEBHOOKS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = key(request);
        Optional<Duration> remaining = registry.check(key);
        if (remaining.isPresent()) {
            reject(response, remaining.get());
            return;
        }
        Optional<RateLimitBlockRegistry.Block> created = registry.register(key);
        if (created.isPresent()) {
            record(created.get());
            reject(response, created.get().duration());
            return;
        }
        chain.doFilter(request, response);
    }

    private static String key(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            return RateLimitBlockRegistry.userKey(principal.userId());
        }
        return RateLimitBlockRegistry.ipKey(HttpExchange.clientIp(request));
    }

    private void record(RateLimitBlockRegistry.Block block) {
        try {
            recorder.recordBlocked(block);
        } catch (RuntimeException e) { // the block stands; a failed history write must not turn 429 into 500
            LOG.warn("Recording the rate-limit block of {} failed: {}", block.scope(), e.getMessage());
        }
    }

    private void reject(HttpServletResponse response, Duration remaining) throws IOException {
        long seconds = Math.max(1, (remaining.toMillis() + 999) / 1000);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        errorWriter.write(response, 429, ApiErrorCode.TOO_MANY_REQUESTS, "Too many requests");
    }
}
