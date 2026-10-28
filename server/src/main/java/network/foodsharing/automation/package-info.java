/**
 * Store and region automations: pickup and request policies, open-slot adverts, sign-out alerts, extra slot,
 * audits and metrics (plan §14.2, docs/conventions.md A24). Depends on store, pickup, region, user, notification and
 * history; reacts to their domain events, never the other way round.
 */
@ApplicationModule(displayName = "Automation")
package network.foodsharing.automation;

import org.springframework.modulith.ApplicationModule;
