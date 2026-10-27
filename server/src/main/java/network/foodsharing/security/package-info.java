/**
 * Authentication plumbing shared by every module: the session cookie filter, the principal, the current actor and
 * the permission service. Depends on no feature module; the auth module plugs in via {@link
 * network.foodsharing.security.SessionAuthenticator}.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package network.foodsharing.security;

import org.springframework.modulith.ApplicationModule;
