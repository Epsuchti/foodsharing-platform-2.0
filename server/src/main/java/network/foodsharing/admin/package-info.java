/**
 * ORGA operations pages: scheduled jobs, integrations (circuit breakers) and the mail outbox
 * (docs/conventions.md A25-A27). Depends on notification and the shared common.jobs / common.integration.
 */
@ApplicationModule(displayName = "Admin")
package network.foodsharing.admin;

import org.springframework.modulith.ApplicationModule;
