package network.foodsharing.profile.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.profile.domain.UserWallPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWallPostRepository extends JpaRepository<UserWallPost, UUID> {

    Page<UserWallPost> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<UserWallPost> findByUserIdOrAuthorId(UUID userId, UUID authorId);
}
