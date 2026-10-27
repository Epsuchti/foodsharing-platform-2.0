package network.foodsharing.testsupport;

import network.foodsharing.security.SessionAuthenticationToken;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Principals for tests. */
public final class TestAuth {

    private TestAuth() {
    }

    public static SessionPrincipal principal(SeedUsers user) {
        return new SessionPrincipal(user.id(), SeedUsers.TEST_SESSION_ID, user.role(), user.email());
    }

    /** MockMvc post processor: request authenticated as the seed user. */
    public static RequestPostProcessor as(SeedUsers user) {
        return SecurityMockMvcRequestPostProcessors.authentication(SessionAuthenticationToken.of(principal(user)));
    }

    /** Sets the SecurityContext of the current thread (service tests); clear with {@link #clear()}. */
    public static void login(SessionPrincipal principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SessionAuthenticationToken.of(principal));
        SecurityContextHolder.setContext(context);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
