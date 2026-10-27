package network.foodsharing.content.service;

import java.util.UUID;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.quiz.service.QuizConsentChecker;
import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * How the content module plugs into other modules: pending consents in the session info (A11), the consent check of
 * the quiz confirmation, and who may read CONTENT history (content editors; ORGA always).
 */
@Configuration(proxyBeanMethods = false)
public class LegalIntegrations {

    @Bean
    @Order(20)
    SessionInfoEnricher legalSessionInfoEnricher(LegalService legal) {
        return (SessionPrincipal principal, SessionInfoBuilder builder) ->
                legal.pendingConsents(principal.userId()).forEach(kind -> builder.addPendingConsent(kind.name()));
    }

    @Bean
    QuizConsentChecker legalQuizConsentChecker(LegalService legal) {
        return (UUID userId, String kind) -> legal.hasCurrentConsent(userId, LegalDocumentKind.valueOf(kind));
    }

    @Bean
    HistoryAccessRule contentHistoryAccessRule(ContentService content) {
        return new HistoryAccessRule() {
            @Override
            public SubjectType subjectType() {
                return SubjectType.CONTENT;
            }

            @Override
            public boolean canRead(SessionPrincipal actor, UUID subjectId) {
                return actor != null && content.mayEdit(actor);
            }
        };
    }
}
