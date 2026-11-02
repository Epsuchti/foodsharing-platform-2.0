package network.foodsharing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserSearchService;
import org.junit.jupiter.api.Test;

/** Decision 147: pickers find contacts only (ORGA everyone); an {@code @} query is an exact, scoped email search. */
class UserSearchServiceTest {

    private static final UUID REGION = UUID.randomUUID();

    private final UserRepository users = mock(UserRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final UserSearchService service = new UserSearchService(users, actors, permissions);

    private SessionPrincipal actAs(Role role) {
        SessionPrincipal principal = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "me@local.test");
        when(actors.requirePrincipal()).thenReturn(principal);
        when(permissions.isOrga(principal)).thenReturn(role == Role.ORGA);
        return principal;
    }

    @Test
    void namesOfContactsOnlyExceptForOrga() {
        SessionPrincipal foodsaver = actAs(Role.FOODSAVER);
        service.search(" Ni_n% ", null, 50);
        verify(users).searchContactsByNamePrefix("nin%", null, foodsaver.userId(), 20);
        verify(users, never()).searchByNamePrefix(any(), any(), anyInt());

        actAs(Role.ORGA);
        service.search("Nina", REGION, 5);
        verify(users).searchByNamePrefix("nina%", REGION, 5);
    }

    @Test
    void emailExactForOrgaAndScopedForUserManagers() {
        User nina = TestUsers.user("nina@test.local", Role.FOODSAVER);
        when(users.findByEmailIgnoreCase("nina@test.local")).thenReturn(Optional.of(nina));

        actAs(Role.ORGA);
        assertThat(service.search("nina@test.local", null, 10)).containsExactly(nina);

        SessionPrincipal manager = actAs(Role.AMBASSADOR);
        when(permissions.regionIdsWith(manager, Permission.CAN_MANAGE_USERS)).thenReturn(Set.of(REGION));
        when(users.countActiveMemberships(nina.getId(), Set.of(REGION))).thenReturn(1L);
        assertThat(service.search("nina@test.local", null, 10)).containsExactly(nina);
        when(users.countActiveMemberships(nina.getId(), Set.of(REGION))).thenReturn(0L);
        assertThat(service.search("nina@test.local", null, 10)).isEmpty();

        SessionPrincipal other = actAs(Role.FOODSAVER);
        when(permissions.regionIdsWith(other, Permission.CAN_MANAGE_USERS)).thenReturn(Set.of());
        assertThat(service.search("nina@test.local", null, 10)).isEmpty();
        verify(users, never()).searchContactsByNamePrefix(any(), any(), any(), anyInt());
    }

    @Test
    void inactiveUsersAreNotFoundByEmail() {
        User gone = TestUsers.user("gone@test.local", Role.FOODSAVER);
        gone.setActive(false);
        when(users.findByEmailIgnoreCase("gone@test.local")).thenReturn(Optional.of(gone));
        actAs(Role.ORGA);
        assertThat(service.search("gone@test.local", null, 10)).isEqualTo(List.of());
    }
}
