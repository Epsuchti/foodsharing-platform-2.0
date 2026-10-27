/**
 * Development helpers, present only in the {@code local} and {@code e2e} profiles (every class is
 * {@code @Profile({"local","e2e"})}, ArchUnit rule 6): dev user switcher, seed reset, captured mails, server clock and
 * the embedded fake OIDC provider that stands in for Google (decision #2).
 */
@ApplicationModule(displayName = "Dev")
package network.foodsharing.dev;

import org.springframework.modulith.ApplicationModule;
