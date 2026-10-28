package network.foodsharing.auth.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of the auth module (A40): sessions are revoked (rows stay for the security log until their
 * retention ends), login identities, passkeys, magic-link tokens and email changes are deleted.
 */
@Component
public class AuthAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;
    private final AuthSessionService sessions;
    private final UserService users;

    public AuthAccountData(EntityManager em, AuthSessionService sessions, UserService users) {
        this.em = em;
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        sessions.revokeAllExcept(userId, null, SessionRevokeReason.ACCOUNT_DELETED);
        UserRows.delete(em, "AuthIdentity", "user.id", userId);
        UserRows.delete(em, "PasskeyCredential", "userId", userId);
        UserRows.delete(em, "PasskeyUserEntity", "userId", userId);
        UserRows.delete(em, "EmailChange", "userId", userId);
        users.findActive(userId).ifPresent(user -> em.createQuery("delete from LoginToken t where t.email = :email")
                .setParameter("email", user.getEmail()).executeUpdate());
    }

    @Override
    public String section() {
        return "sessions";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessions", UserRows.read(em, "AuthSession", "user.id", userId, "createdAt", "lastSeenAt",
                "expiresAt", "userAgent", "revokedAt", "revokedReason"));
        result.put("loginMethods", UserRows.read(em, "AuthIdentity", "user.id", userId, "provider", "label",
                "createdAt", "lastUsedAt"));
        result.put("passkeys", UserRows.read(em, "PasskeyCredential", "userId", userId, "label", "createdAt",
                "lastUsedAt"));
        result.put("emailChanges", UserRows.read(em, "EmailChange", "userId", userId, "newEmail", "createdAt",
                "completedAt", "abortedAt"));
        return result;
    }
}
