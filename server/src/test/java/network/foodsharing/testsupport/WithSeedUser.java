package network.foodsharing.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Runs a test as a seed user (a {@code SessionPrincipal} with the user's fixed id and role), e.g.
 * {@code @WithSeedUser("orga")}. Does not need the user in the database.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@WithSecurityContext(factory = WithSeedUserSecurityContextFactory.class)
public @interface WithSeedUser {

    /** Seed user name (case-insensitive), see {@link SeedUsers}. */
    String value() default "foodsaver";
}
