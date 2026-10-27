package network.foodsharing.testsupport;

import network.foodsharing.security.SessionAuthenticationToken;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithSeedUserSecurityContextFactory implements WithSecurityContextFactory<WithSeedUser> {

    @Override
    public SecurityContext createSecurityContext(WithSeedUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SessionAuthenticationToken.of(TestAuth.principal(SeedUsers.byName(annotation.value()))));
        return context;
    }
}
