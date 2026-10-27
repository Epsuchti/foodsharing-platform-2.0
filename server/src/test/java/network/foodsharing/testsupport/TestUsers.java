package network.foodsharing.testsupport;

import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;

/** Unsaved {@link User} entities for repository/service tests. */
public final class TestUsers {

    private TestUsers() {
    }

    public static User user(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(email.substring(0, email.indexOf('@')));
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPreferredLanguage(Language.DE);
        user.setRole(role);
        user.setActive(true);
        user.setRegisteredAt(FixedClockConfig.SEED_NOW);
        return user;
    }

    public static User user(String email) {
        return user(email, Role.FOODSAVER);
    }

    /** Principal for a saved user (session id is a fixed dummy unless given). */
    public static SessionPrincipal principal(User user) {
        return new SessionPrincipal(user.getId(), SeedUsers.TEST_SESSION_ID, user.getRole(), user.getEmail());
    }
}
