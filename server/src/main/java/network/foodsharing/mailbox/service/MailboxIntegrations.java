package network.foodsharing.mailbox.service;

import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** SessionInfo.mailboxes: whether the menu shows Postfächer (decision 149). */
@Configuration(proxyBeanMethods = false)
public class MailboxIntegrations {

    @Bean
    @Order(40)
    SessionInfoEnricher mailboxSessionInfoEnricher(MailboxAccessService access) {
        return (SessionPrincipal principal, SessionInfoBuilder builder) ->
                builder.mailboxes(access.showsMailboxes(principal));
    }
}
