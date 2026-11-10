package network.foodsharing.verification.service;

import network.foodsharing.user.domain.User;

/**
 * Pure verification rules (Ve2, C1): nobody is verified before an administrator has confirmed their identity. There
 * is no ORGA exception; un-verifying, reverting the identity and users verified earlier are not affected.
 */
public final class VerificationRules {

    private VerificationRules() {
    }

    /** True when the user's identity has been confirmed (the precondition for verifying them). */
    public static boolean mayVerify(User user) {
        return user.getIdentityConfirmedAt() != null;
    }
}
