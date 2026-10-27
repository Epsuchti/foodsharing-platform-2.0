package network.foodsharing.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates API requests from the {@code fs_session} cookie. Invalid/expired cookies are cleared so the browser
 * stops sending them. Created by {@code config.SecurityConfig}; deliberately not a bean (no servlet registration).
 */
public class SessionCookieAuthenticationFilter extends OncePerRequestFilter {

    private final SessionAuthenticator authenticator;
    private final SessionCookieWriter cookieWriter;

    public SessionCookieAuthenticationFilter(SessionAuthenticator authenticator, SessionCookieWriter cookieWriter) {
        this.authenticator = authenticator;
        this.cookieWriter = cookieWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> token = cookieWriter.read(request);
        if (token.isPresent() && !alreadyAuthenticated()) {
            Optional<SessionPrincipal> principal = authenticator.authenticate(token.get());
            if (principal.isPresent()) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(SessionAuthenticationToken.of(principal.get()));
                SecurityContextHolder.setContext(context);
            } else {
                cookieWriter.clear(response);
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean alreadyAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof SessionAuthenticationToken;
    }
}
