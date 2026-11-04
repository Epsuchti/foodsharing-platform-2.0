package network.foodsharing.notification.newsletter.service;

import network.foodsharing.security.NavCapabilities;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * SessionInfo.capabilities NEWSLETTERS (decision 162): ORGA and those who administrate a region, the scope that
 * {@link NewsletterService} lets create newsletters (requireRegion). Working-group admins cannot create any.
 */
@Configuration(proxyBeanMethods = false)
public class NewsletterIntegrations {

    @Bean
    @Order(52)
    SessionInfoEnricher newsletterSessionInfoEnricher(PermissionService permissions) {
        return (SessionPrincipal principal, SessionInfoBuilder builder) -> {
            boolean mayAdministrate = permissions.isOrga(principal)
                    || (builder.adminOfAnyUnit() && permissions.mayAdministrateAnyRegion(principal));
            if (mayAdministrate) {
                builder.capability(NavCapabilities.NEWSLETTERS);
            }
        };
    }
}
