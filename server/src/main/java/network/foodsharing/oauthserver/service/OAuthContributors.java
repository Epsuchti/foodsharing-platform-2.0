package network.foodsharing.oauthserver.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** SPI implementations of the OAuth provider (A40, history access). */
final class OAuthContributors {

    private OAuthContributors() {
    }

    /** A40: consents, refresh tokens and pending requests go with the account; the export lists connected apps. */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final OAuthGrantService grants;

        AccountData(OAuthGrantService grants) {
            this.grants = grants;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            grants.removeUser(userId);
        }

        @Override
        public String section() {
            return "oauthConnections";
        }

        @Override
        public Object export(UUID userId) {
            return grants.export(userId);
        }
    }

    /** A client's history (creation, consents, token issuance): ORGA only. */
    @Component
    static class HistoryAccess implements HistoryAccessRule {

        private final PermissionService permissions;

        HistoryAccess(PermissionService permissions) {
            this.permissions = permissions;
        }

        @Override
        public SubjectType subjectType() {
            return SubjectType.OAUTH_CLIENT;
        }

        @Override
        public boolean canRead(SessionPrincipal actor, UUID subjectId) {
            return permissions.isOrga(actor);
        }
    }
}
