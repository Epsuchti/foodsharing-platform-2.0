package network.foodsharing.security;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.user.domain.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Who is acting in the current request. Services use this (never the SecurityContext directly) and check
 * permissions via {@link PermissionService}.
 */
@Service
public class CurrentActorService {

    private final PermissionService permissionService;
    private final ObjectProvider<EntityManager> entityManager;

    public CurrentActorService(PermissionService permissionService, ObjectProvider<EntityManager> entityManager) {
        this.permissionService = permissionService;
        this.entityManager = entityManager;
    }

    public Optional<SessionPrincipal> principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public Optional<UUID> currentUserId() {
        return principal().map(SessionPrincipal::userId);
    }

    /** @throws ApiException 401 AUTHENTICATION_REQUIRED */
    public SessionPrincipal requirePrincipal() {
        return principal().orElseThrow(ApiException::unauthenticated);
    }

    /** @throws ApiException 401 AUTHENTICATION_REQUIRED */
    public UUID requireUserId() {
        return requirePrincipal().userId();
    }

    /**
     * The signed-in, active user entity (managed in the caller's transaction, if any).
     *
     * @throws ApiException 401 AUTHENTICATION_REQUIRED when not signed in or the account is gone/disabled
     */
    public User requireUser() {
        UUID userId = requireUserId();
        User user = entityManager.getObject().find(User.class, userId);
        if (user == null || !user.isActive()) {
            throw ApiException.unauthenticated();
        }
        return user;
    }

    /** @throws ApiException 401 when not signed in, 403 ACCESS_DENIED when the role is lower */
    public SessionPrincipal requireRole(Role role) {
        SessionPrincipal principal = requirePrincipal();
        if (!permissionService.hasRole(principal, role)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return principal;
    }

    public boolean hasRole(Role role) {
        return principal().map(principal -> permissionService.hasRole(principal, role)).orElse(false);
    }

    public boolean isOrga() {
        return principal().map(permissionService::isOrga).orElse(false);
    }
}
