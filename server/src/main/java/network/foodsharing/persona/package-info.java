/**
 * Persona panel of the public test environment (decision 173): the seed personas of
 * {@link network.foodsharing.common.seed.SeedPersonas} behind a server-checked password. Every class is
 * {@code @Profile("staging")} (ArchUnit rule 6b) and conditional on a non-blank
 * {@code app.staging.persona-panel.password} ({@link network.foodsharing.config.PersonaPanelCondition}); it never
 * reuses the dev API, which logs in as any user.
 */
@ApplicationModule(displayName = "Personas")
package network.foodsharing.persona;

import org.springframework.modulith.ApplicationModule;
