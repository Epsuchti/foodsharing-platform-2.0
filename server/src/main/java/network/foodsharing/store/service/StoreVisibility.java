package network.foodsharing.store.service;

import network.foodsharing.api.model.Role;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may know which stores work with foodsharing (decision 156, S17): ORGA and verified people, nobody else. The
 * check is {@code User.verified}, not the role: the quiz raises the role and un-verifying leaves it, so FOODSAVER and
 * unverified occur together. {@code verified} is not in the session and verifying does not revoke sessions, so it is
 * read live.
 */
@Service
public class StoreVisibility {

    private final UserService users;

    public StoreVisibility(UserService users) {
        this.users = users;
    }

    /** The rule. */
    public static boolean mayKnow(boolean orga, boolean verified) {
        return orga || verified;
    }

    public static boolean mayKnow(User user) {
        return mayKnow(user.getRole() == Role.ORGA, user.isVerified());
    }

    /** @param actor null for anonymous visitors, who may not */
    @Transactional(readOnly = true)
    public boolean mayKnowStores(SessionPrincipal actor) {
        return mayKnow(actor != null && actor.role() == Role.ORGA, isVerified(actor));
    }

    /** {@code User.verified} of the actor, read live; false for anonymous visitors and disabled accounts. */
    @Transactional(readOnly = true)
    public boolean isVerified(SessionPrincipal actor) {
        return actor != null && users.findActive(actor.userId()).map(User::isVerified).orElse(false);
    }
}
