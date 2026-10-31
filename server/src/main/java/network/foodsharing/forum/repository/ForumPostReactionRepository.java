package network.foodsharing.forum.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumPostReaction;
import network.foodsharing.forum.domain.ReactionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumPostReactionRepository extends JpaRepository<ForumPostReaction, UUID> {

    List<ForumPostReaction> findByPostIdInOrderByCreatedAtAscIdAsc(Collection<UUID> postIds);

    Optional<ForumPostReaction> findByPostIdAndUserIdAndReactionKey(UUID postId, UUID userId, ReactionKey key);
}
