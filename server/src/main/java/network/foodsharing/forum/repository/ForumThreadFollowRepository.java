package network.foodsharing.forum.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumThreadFollow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumThreadFollowRepository extends JpaRepository<ForumThreadFollow, UUID> {

    List<ForumThreadFollow> findByThreadId(UUID threadId);

    Optional<ForumThreadFollow> findByThreadIdAndUserId(UUID threadId, UUID userId);

    List<ForumThreadFollow> findByUserIdAndThreadIdIn(UUID userId, Collection<UUID> threadIds);
}
