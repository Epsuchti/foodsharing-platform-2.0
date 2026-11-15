package network.foodsharing.content.service;

import network.foodsharing.api.model.Role;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.security.PermissionService;

/**
 * Which legal documents a user has to accept (plan §13.9 Ve1): the PRIVACY_POLICY everybody, the LEGAL_AGREEMENT
 * foodsavers (role or quiz role at least FOODSAVER, journey step 5), the CONFIDENTIALITY_NOTICE store managers and up.
 */
public final class LegalConsentRules {

    private LegalConsentRules() {
    }

    public static boolean required(Role role, Role quizRole, LegalDocumentKind kind) {
        return switch (kind) {
            case PRIVACY_POLICY -> true;
            case LEGAL_AGREEMENT -> PermissionService.isAtLeast(role, Role.FOODSAVER)
                    || PermissionService.isAtLeast(quizRole, Role.FOODSAVER);
            case CONFIDENTIALITY_NOTICE -> PermissionService.isAtLeast(role, Role.STORE_MANAGER);
        };
    }
}
