package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;

/** Decision 153, S17 D1: {@code User.verified}, never the role; ORGA is exempt. */
class StoreVisibilityTest {

    private final UserService users = mock(UserService.class);
    private final StoreVisibility visibility = new StoreVisibility(users);

    private SessionPrincipal actor(Role role, boolean verified) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setVerified(verified);
        when(users.findActive(user.getId())).thenReturn(Optional.of(user));
        return new SessionPrincipal(user.getId(), UUID.randomUUID(), role, "a@local");
    }

    @Test
    void anonymousVisitorsMayNot() {
        assertThat(visibility.mayKnowStores(null)).isFalse();
    }

    @Test
    void theRoleDoesNotCount() {
        assertThat(visibility.mayKnowStores(actor(Role.FOODSAVER, false))).isFalse();
        assertThat(visibility.mayKnowStores(actor(Role.STORE_MANAGER, false))).isFalse();
        assertThat(visibility.mayKnowStores(actor(Role.AMBASSADOR, false))).isFalse();
    }

    @Test
    void verifiedPeopleMay() {
        assertThat(visibility.mayKnowStores(actor(Role.FOODSAVER, true))).isTrue();
    }

    @Test
    void orgaIsExempt() {
        assertThat(visibility.mayKnowStores(actor(Role.ORGA, false))).isTrue();
    }

    @Test
    void aDisabledAccountMayNot() {
        UUID id = UUID.randomUUID();
        when(users.findActive(id)).thenReturn(Optional.empty());
        assertThat(visibility.mayKnowStores(new SessionPrincipal(id, null, Role.FOODSAVER, "a@local"))).isFalse();
    }

    @Test
    void theUserVariantFollowsTheSameRule() {
        User user = new User();
        user.setRole(Role.STORE_MANAGER);
        assertThat(StoreVisibility.mayKnow(user)).isFalse();
        user.setVerified(true);
        assertThat(StoreVisibility.mayKnow(user)).isTrue();
        user.setVerified(false);
        user.setRole(Role.ORGA);
        assertThat(StoreVisibility.mayKnow(user)).isTrue();
    }
}
