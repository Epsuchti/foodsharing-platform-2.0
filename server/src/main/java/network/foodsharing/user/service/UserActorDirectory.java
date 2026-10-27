package network.foodsharing.user.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.history.ActorDirectory;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Display names and avatars of history actors. */
@Component
public class UserActorDirectory implements ActorDirectory {

    private final UserRepository repository;

    public UserActorDirectory(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ActorView> find(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdIn(userIds).stream()
                .map(user -> new ActorView(user.getId(), user.getDisplayName(), user.getAvatarUrl()))
                .collect(Collectors.toMap(ActorView::id, Function.identity()));
    }
}
