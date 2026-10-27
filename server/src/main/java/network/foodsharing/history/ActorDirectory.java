package network.foodsharing.history;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Display data of actors for history views. Implemented by the user module, so the history module does not depend
 * on it.
 */
public interface ActorDirectory {

    Map<UUID, ActorView> find(Collection<UUID> userIds);

    record ActorView(UUID id, String displayName, String avatarUrl) {
    }
}
