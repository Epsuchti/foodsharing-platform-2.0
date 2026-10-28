package network.foodsharing.forum.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumFollow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumFollowRepository extends JpaRepository<ForumFollow, UUID> {

    List<ForumFollow> findByRegionIdAndAmbassadorForum(UUID regionId, boolean ambassadorForum);

    Optional<ForumFollow> findByRegionIdAndAmbassadorForumAndUserId(UUID regionId, boolean ambassadorForum,
                                                                    UUID userId);
}
