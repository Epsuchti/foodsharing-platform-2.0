package network.foodsharing.content.service;

import network.foodsharing.security.NavCapabilities;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * SessionInfo.capabilities of the content module (decision 162): BLOG_MANAGE for blog editors and ambassadors of a
 * region, CONTENT_EDIT for ORGA and PR group admins.
 */
@Configuration(proxyBeanMethods = false)
public class ContentCapabilities {

    @Bean
    @Order(51)
    SessionInfoEnricher contentSessionInfoEnricher(BlogService blogs, PermissionService permissions) {
        return (SessionPrincipal principal, SessionInfoBuilder builder) -> {
            if (!permissions.isOrga(principal) && !builder.adminOfAnyUnit()) {
                return; // editors and ambassadors administrate a unit
            }
            BlogService.MenuRights rights = blogs.menuRights(principal);
            if (rights != BlogService.MenuRights.NONE) {
                builder.capability(NavCapabilities.BLOG_MANAGE);
            }
            if (rights == BlogService.MenuRights.EDITOR) {
                builder.capability(NavCapabilities.CONTENT_EDIT);
            }
        };
    }
}
