package network.foodsharing.verification.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.verification.domain.PassportRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassportRequestRepository extends JpaRepository<PassportRequest, UUID> {

    boolean existsByUserIdAndFulfilledAtIsNull(UUID userId);

    List<PassportRequest> findByUserIdAndFulfilledAtIsNull(UUID userId);

    List<PassportRequest> findByUserIdInOrderByRequestedAtDesc(Collection<UUID> userIds);
}
