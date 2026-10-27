package network.foodsharing.quiz.service;

import java.util.UUID;

/**
 * Legal consents the quiz confirmation needs (store manager → CONFIDENTIALITY_NOTICE), implemented by the content
 * module ({@code LegalService}); without an implementation no consent exists.
 */
public interface QuizConsentChecker {

    /** {@code legalDocumentKind} is the LegalDocumentKind name. */
    boolean hasCurrentConsent(UUID userId, String legalDocumentKind);
}
