package network.foodsharing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Outside the {@code local}/{@code e2e} profiles the dev paths (dev API, fake OIDC provider, H2 console) answer
 * 404 {@code RESOURCE_NOT_FOUND} before Spring Security runs, for anonymous and logged-in callers alike, so
 * production does not reveal that they exist (D19.1 smoke test). The SPA's own {@code /dev/...} pages are unaffected.
 */
@Component
@Profile({"!local & !e2e"})
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class DevPathsNotFoundFilter extends OncePerRequestFilter {

    static final List<String> PREFIXES = List.of("/api/dev", "/dev/oidc", "/h2-console");

    private final ApiErrorResponseWriter errorWriter;

    public DevPathsNotFoundFilter(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PREFIXES.stream().noneMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException {
        errorWriter.write(response, HttpServletResponse.SC_NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Not found");
    }
}
