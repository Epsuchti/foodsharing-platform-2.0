package network.foodsharing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The public test environment (staging profile, decision 173) stays out of search engines: every response carries
 * {@code X-Robots-Tag: noindex, nofollow} (pages, API, static files).
 */
@Component
@Profile(StagingProfileGuard.STAGING)
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class StagingNoIndexFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Robots-Tag";
    public static final String VALUE = "noindex, nofollow";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, VALUE);
        chain.doFilter(request, response);
    }
}
