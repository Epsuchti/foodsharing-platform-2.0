package network.foodsharing.content.service;

import static network.foodsharing.content.domain.LegalDocumentKind.PRIVACY_POLICY;
import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.api.model.Role;
import network.foodsharing.content.domain.LegalDocumentKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Ve1 / EA4: who has to accept which legal document. A newcomer only the privacy policy (the legal agreement comes with
 * the foodsaver quiz confirmation), a foodsaver also the agreement, a store manager (also one just promoted) also the
 * confidentiality notice.
 */
class LegalConsentRulesTest {

    @ParameterizedTest(name = "{0} (quiz role {1}): {2} → {3}")
    @CsvSource(nullValues = "NULL", value = {
            "FOODSHARER, NULL, PRIVACY_POLICY, true",
            "FOODSHARER, NULL, LEGAL_AGREEMENT, false",
            "FOODSHARER, NULL, CONFIDENTIALITY_NOTICE, false",
            // passed the foodsaver quiz but not confirmed yet: the agreement is due (journey step AGREEMENT)
            "FOODSHARER, FOODSAVER, LEGAL_AGREEMENT, true",
            "FOODSHARER, FOODSAVER, CONFIDENTIALITY_NOTICE, false",
            "FOODSAVER, NULL, PRIVACY_POLICY, true",
            "FOODSAVER, NULL, LEGAL_AGREEMENT, true",
            "FOODSAVER, NULL, CONFIDENTIALITY_NOTICE, false",
            // the quiz role never raises the confidentiality notice, only the role does
            "FOODSAVER, STORE_MANAGER, CONFIDENTIALITY_NOTICE, false",
            "STORE_MANAGER, NULL, LEGAL_AGREEMENT, true",
            "STORE_MANAGER, NULL, CONFIDENTIALITY_NOTICE, true",
            "AMBASSADOR, NULL, CONFIDENTIALITY_NOTICE, true",
            "ORGA, NULL, PRIVACY_POLICY, true",
            "ORGA, NULL, LEGAL_AGREEMENT, true",
            "ORGA, NULL, CONFIDENTIALITY_NOTICE, true"})
    void requiredDocumentsFollowTheRole(Role role, Role quizRole, LegalDocumentKind kind, boolean required) {
        assertThat(LegalConsentRules.required(role, quizRole, kind)).isEqualTo(required);
    }

    @ParameterizedTest
    @EnumSource(LegalDocumentKind.class)
    void noRoleMeansOnlyThePrivacyPolicy(LegalDocumentKind kind) {
        assertThat(LegalConsentRules.required(null, null, kind)).isEqualTo(kind == PRIVACY_POLICY);
    }
}
