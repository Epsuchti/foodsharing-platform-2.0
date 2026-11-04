package network.foodsharing.quiz.service;

import network.foodsharing.security.NavCapabilities;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** SessionInfo.capabilities QUIZ_EDIT: the menu item Quizze bearbeiten (decision 159). */
@Configuration(proxyBeanMethods = false)
public class QuizIntegrations {

    @Bean
    @Order(50)
    SessionInfoEnricher quizSessionInfoEnricher(QuizEditorService editors, PermissionService permissions) {
        return (SessionPrincipal principal, SessionInfoBuilder builder) -> {
            // quiz editors are ORGA or admins of a quiz group: skip the query for users who administrate nothing
            if ((permissions.isOrga(principal) || builder.adminOfAnyUnit()) && editors.mayEditAny(principal)) {
                builder.capability(NavCapabilities.QUIZ_EDIT);
            }
        };
    }
}
