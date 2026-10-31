package network.foodsharing.region.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.GroupApplication;
import network.foodsharing.region.domain.GroupApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupApplicationRepository extends JpaRepository<GroupApplication, UUID> {

    List<GroupApplication> findByGroupIdAndStatusOrderByCreatedAtAscIdAsc(UUID groupId, GroupApplicationStatus status);

    Optional<GroupApplication> findFirstByGroupIdAndUserIdAndStatus(UUID groupId, UUID userId,
                                                                    GroupApplicationStatus status);

    List<GroupApplication> findByUserId(UUID userId);

    Optional<GroupApplication> findByIdAndGroupId(UUID id, UUID groupId);

    List<GroupApplication> findByGroupIdOrderByCreatedAtDescIdDesc(UUID groupId);
}
