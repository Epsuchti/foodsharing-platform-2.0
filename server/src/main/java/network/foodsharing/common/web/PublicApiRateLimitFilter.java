package network.foodsharing.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.config.AppProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * D17.2 (OWASP A04, ASVS 11.1.4): per-IP sliding-window limit on the anonymous API {@code /api/public/**}, as
 * documented by its 429 TOO_MANY_REQUESTS responses in the contract. Runs before Spring Security, so floods never
 * reach a session lookup or the database. Endpoint-specific limits (login, support form, client errors) stay in
 * their services and are stricter. {@code app.security.public-api-requests-per-minute-per-ip=0} disables it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    static final String PREFIX = "/api/public/";
    /** Machine-to-machine webhooks authenticated by a secret in the path (mail server, Telegram): not limited. */
    static final List<String> WEBHOOKS = List.of("/api/public/mailbox/inbound/",
            "/api/public/telegram/webhook/");

    private final ApiErrorResponseWriter errorWriter;
    private final SlidingWindowRateLimiter limiter;

    public PublicApiRateLimitFilter(AppProperties properties, Clock clock, ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
        // slice tests mock AppProperties (as SecurityConfig does: fall back to the defaults)
        AppProperties.Security security = properties.getSecurity() == null
                ? new AppProperties.Security() : properties.getSecurity();
        int limit = security.getPublicApiRequestsPerMinutePerIp();
        this.limiter = limit > 0 ? new SlidingWindowRateLimiter("public-api", clock, limit, Duration.ofMinutes(1)) : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return limiter == null || !path.startsWith(PREFIX) || WEBHOOKS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.tryAcquire(HttpExchange.clientIp(request))) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(limiter.window().toSeconds()));
            errorWriter.write(response, 429, ApiErrorCode.TOO_MANY_REQUESTS, "Too many requests");
            return;
        }
        chain.doFilter(request, response);
    }
}
