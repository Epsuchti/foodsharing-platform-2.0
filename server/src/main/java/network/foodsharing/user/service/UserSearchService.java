package network.foodsharing.user.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User pickers (chat members, mailbox members, co-authors; A33): name prefix over active users. */
@Service
public class UserSearchService {

    private final UserRepository users;
    private final CurrentActorService currentActor;

    public UserSearchService(UserRepository users, CurrentActorService currentActor) {
        this.users = users;
        this.currentActor = currentActor;
    }

    /** Non-ORGA callers only find users who share a region or group with them. */
    @Transactional(readOnly = true)
    public List<User> search(String query, UUID regionId, int limit) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        String prefix = query.strip().toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "") + "%";
        UUID sharedWith = currentActor.isOrga() ? null : actor.userId();
        return users.searchByNamePrefix(prefix, regionId, sharedWith, Math.max(1, Math.min(limit, 20)));
    }
}
