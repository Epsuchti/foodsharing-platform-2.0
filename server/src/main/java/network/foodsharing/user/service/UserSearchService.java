package network.foodsharing.user.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User pickers (chat members, mailbox members, co-authors; A33). A name prefix over active users: ORGA finds everyone,
 * others only their contacts ({@link network.foodsharing.user.domain.UserContacts}). A query with {@code @} is an exact
 * email search (decision 147): ORGA over all users, holders of CAN_MANAGE_USERS over the active members of those
 * regions, everyone else finds nothing.
 */
@Service
public class UserSearchService {

    private final UserRepository users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;

    public UserSearchService(UserRepository users, CurrentActorService currentActor, PermissionService permissions) {
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<User> search(String query, UUID regionId, int limit) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        String text = query.strip();
        if (text.contains("@")) {
            return byEmail(actor, text, regionId);
        }
        String prefix = text.toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "") + "%";
        int capped = Math.max(1, Math.min(limit, 20));
        return permissions.isOrga(actor) ? users.searchByNamePrefix(prefix, regionId, capped)
                : users.searchContactsByNamePrefix(prefix, regionId, actor.userId(), capped);
    }

    private List<User> byEmail(SessionPrincipal actor, String email, UUID regionId) {
        boolean orga = permissions.isOrga(actor);
        Set<UUID> scope = orga ? Set.of() : permissions.regionIdsWith(actor, Permission.CAN_MANAGE_USERS);
        if (!orga && scope.isEmpty()) {
            return List.of();
        }
        return users.findByEmailIgnoreCase(email).filter(User::isActive)
                .filter(user -> orga || users.countActiveMemberships(user.getId(), scope) > 0)
                .filter(user -> regionId == null || users.countActiveMemberships(user.getId(), Set.of(regionId)) > 0)
                .map(List::of).orElse(List.of());
    }
}
